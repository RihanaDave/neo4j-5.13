/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.index.internal.gbptree;

import static org.neo4j.util.Preconditions.checkState;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.PageCursor;

/**
 * Tree state is defined as top level tree meta data which changes as the tree and its constructs changes, such as:
 * <ul>
 * <li>stable/unstable generation numbers</li>
 * <li>root id, the page id containing the root of the tree</li>
 * <li>last id, the page id which is the highest allocated in the store</li>
 * <li>pointers into free-list (page id + offset)</li>
 * </ul>
 * This class also knows how to
 * {@link #write(PageCursor, long, long, long, long, long, long, long, int, int, boolean) write} and
 * {@link #read(PageCursor) read} tree state to and from a {@link PageCursor}, although doesn't care where
 * in the store that is.
 */
class TreeState {
    /**
     * Size of one set of tree-state fields.
     */
    private static final int TREE_STATE_FIELDS_SIZE = Integer.BYTES * 2 // stable/unstable gen
            + Long.BYTES // rootId
            + Long.BYTES // rootGeneration
            + Long.BYTES // lastId
            + Long.BYTES // freeListWritePageId
            + Long.BYTES // freeListReadPageId
            + Integer.BYTES // freeListWritePos
            + Integer.BYTES // freeListReadPos
            + Byte.BYTES; // clean

    /**
     * Size of a tree-state altogether, which consists of two sets of tree-state fields.
     */
    static final int SIZE = TREE_STATE_FIELDS_SIZE * 2;

    private static final byte CLEAN_BYTE = 0x01;
    private static final byte DIRTY_BYTE = 0x00;

    /**
     * Page id this tree state has been read from.
     */
    private final long pageId;

    /**
     * Stable generation of the tree.
     */
    private final long stableGeneration;

    /**
     * Unstable generation of the tree.
     */
    private final long unstableGeneration;

    /**
     * Page id which is the root of the tree.
     */
    private final long rootId;

    /**
     * Generation of {@link #rootId}.
     */
    private final long rootGeneration;

    /**
     * Highest allocated page id in the store. This id may not be in use currently and cannot decrease
     * since {@link PageCache} doesn't allow shrinking files.
     */
    private final long lastId;

    /**
     * Page id to write new released tree node ids into.
     */
    private final long freeListWritePageId;

    /**
     * Page id to read released tree node ids from, when acquiring ids.
     */
    private final long freeListReadPageId;

    /**
     * Offset in page {@link #freeListWritePageId} to write new released tree node ids at.
     */
    private final int freeListWritePos;

    /**
     * Offset in page {@link #freeListReadPageId} to read released tree node ids from, when acquiring ids.
     */
    private final int freeListReadPos;

    /**
     * Due to writing with potential concurrent page flushing tree state is written twice, the second
     * state acting as checksum. If both states match this variable should be set to {@code true},
     * otherwise to {@code false}.
     */
    private boolean valid;

    /**
     * Is tree clean or dirty. Clean means it was closed without any non-checkpointed changes.
     */
    private final boolean clean;

    TreeState(
            long pageId,
            long stableGeneration,
            long unstableGeneration,
            long rootId,
            long rootGeneration,
            long lastId,
            long freeListWritePageId,
            long freeListReadPageId,
            int freeListWritePos,
            int freeListReadPos,
            boolean clean,
            boolean valid) {
        this.pageId = pageId;
        this.stableGeneration = stableGeneration;
        this.unstableGeneration = unstableGeneration;
        this.rootId = rootId;
        this.rootGeneration = rootGeneration;
        this.lastId = lastId;
        this.freeListWritePageId = freeListWritePageId;
        this.freeListReadPageId = freeListReadPageId;
        this.freeListWritePos = freeListWritePos;
        this.freeListReadPos = freeListReadPos;
        this.clean = clean;
        this.valid = valid;
    }

    long pageId() {
        return pageId;
    }

    long stableGeneration() {
        return stableGeneration;
    }

    long unstableGeneration() {
        return unstableGeneration;
    }

    long rootId() {
        return rootId;
    }

    long rootGeneration() {
        return rootGeneration;
    }

    long lastId() {
        return lastId;
    }

    long freeListWritePageId() {
        return freeListWritePageId;
    }

    long freeListReadPageId() {
        return freeListReadPageId;
    }

    int freeListWritePos() {
        return freeListWritePos;
    }

    int freeListReadPos() {
        return freeListReadPos;
    }

    boolean isValid() {
        return valid;
    }

    /**
     * Writes provided tree state to {@code cursor} at its current offset. Two versions of the state
     * are written after each other, the second one acting as checksum for the first, see {@link #valid} field.
     *
     * @param cursor {@link PageCursor} to write into, at its current offset.
     * @param stableGeneration stable generation.
     * @param unstableGeneration unstable generation.
     * @param rootId root id.
     * @param rootGeneration root generation.
     * @param lastId last id.
     * @param freeListWritePageId free-list page id to write released ids into.
     * @param freeListReadPageId free-list page id to read released ids from.
     * @param freeListWritePos offset into free-list write page id to write released ids into.
     * @param freeListReadPos offset into free-list read page id to read released ids from.
     * @param clean is tree clean or dirty
     */
    static void write(
            PageCursor cursor,
            long stableGeneration,
            long unstableGeneration,
            long rootId,
            long rootGeneration,
            long lastId,
            long freeListWritePageId,
            long freeListReadPageId,
            int freeListWritePos,
            int freeListReadPos,
            boolean clean) {
        GenerationSafePointer.assertGenerationOnWrite(stableGeneration);
        GenerationSafePointer.assertGenerationOnWrite(unstableGeneration);

        writeStateOnce(
                cursor,
                stableGeneration,
                unstableGeneration,
                rootId,
                rootGeneration,
                lastId,
                freeListWritePageId,
                freeListReadPageId,
                freeListWritePos,
                freeListReadPos,
                clean); // Write state
        writeStateOnce(
                cursor,
                stableGeneration,
                unstableGeneration,
                rootId,
                rootGeneration,
                lastId,
                freeListWritePageId,
                freeListReadPageId,
                freeListWritePos,
                freeListReadPos,
                clean); // Write checksum
    }

    /**
     * Reads tree state from {@code cursor} at its current offset. If checksum matches then {@link #valid}
     * is set to {@code true}, otherwise {@code false}.
     *
     * @param cursor {@link PageCursor} to read tree state from, at its current offset.
     * @return {@link TreeState} instance containing read tree state.
     */
    static TreeState read(PageCursor cursor) throws IOException {
        byte[] buffer = new byte[SIZE];
        cursor.getBytes(buffer);
        return read(cursor.getCurrentPageId(), ByteBuffer.wrap(buffer).order(cursor.getByteOrder()));
    }

    /**
     * @see #read(PageCursor)
     *
     * @param pageId current page
     * @param buffer temporary buffer to use
     * @return {@link TreeState} instance containing read tree state.
     */
    static TreeState read(long pageId, ByteBuffer buffer) {
        TreeState state = readStateOnce(pageId, buffer);
        TreeState checksumState = readStateOnce(pageId, buffer);

        boolean valid = state.equals(checksumState);

        boolean isEmpty = state.isEmpty();
        valid &= !isEmpty;

        return state.setValid(valid);
    }

    private TreeState setValid(boolean valid) {
        this.valid = valid;
        return this;
    }

    boolean isEmpty() {
        return stableGeneration == 0L
                && unstableGeneration == 0L
                && rootId == 0L
                && lastId == 0L
                && freeListWritePageId == 0L
                && freeListReadPageId == 0L
                && freeListWritePos == 0
                && freeListReadPos == 0;
    }

    private static TreeState readStateOnce(long pageId, ByteBuffer buffer) {
        checkState(buffer.remaining() >= TREE_STATE_FIELDS_SIZE, "No enough data");
        long stableGeneration = buffer.getInt() & GenerationSafePointer.GENERATION_MASK;
        long unstableGeneration = buffer.getInt() & GenerationSafePointer.GENERATION_MASK;
        long rootId = buffer.getLong();
        long rootGeneration = buffer.getLong();
        long lastId = buffer.getLong();
        long freeListWritePageId = buffer.getLong();
        long freeListReadPageId = buffer.getLong();
        int freeListWritePos = buffer.getInt();
        int freeListReadPos = buffer.getInt();
        boolean clean = buffer.get() == CLEAN_BYTE;
        return new TreeState(
                pageId,
                stableGeneration,
                unstableGeneration,
                rootId,
                rootGeneration,
                lastId,
                freeListWritePageId,
                freeListReadPageId,
                freeListWritePos,
                freeListReadPos,
                clean,
                true);
    }

    private static void writeStateOnce(
            PageCursor cursor,
            long stableGeneration,
            long unstableGeneration,
            long rootId,
            long rootGeneration,
            long lastId,
            long freeListWritePageId,
            long freeListReadPageId,
            int freeListWritePos,
            int freeListReadPos,
            boolean clean) {
        cursor.putInt((int) stableGeneration);
        cursor.putInt((int) unstableGeneration);
        cursor.putLong(rootId);
        cursor.putLong(rootGeneration);
        cursor.putLong(lastId);
        cursor.putLong(freeListWritePageId);
        cursor.putLong(freeListReadPageId);
        cursor.putInt(freeListWritePos);
        cursor.putInt(freeListReadPos);
        cursor.putByte(clean ? CLEAN_BYTE : DIRTY_BYTE);
    }

    @Override
    public String toString() {
        return String.format(
                "pageId=%d, stableGeneration=%d, unstableGeneration=%d, rootId=%d, rootGeneration=%d, "
                        + "lastId=%d, freeListWritePageId=%d, freeListReadPageId=%d, freeListWritePos=%d, freeListReadPos=%d, "
                        + "clean=%b, valid=%b",
                pageId,
                stableGeneration,
                unstableGeneration,
                rootId,
                rootGeneration,
                lastId,
                freeListWritePageId,
                freeListReadPageId,
                freeListWritePos,
                freeListReadPos,
                clean,
                valid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TreeState treeState = (TreeState) o;
        return pageId == treeState.pageId
                && stableGeneration == treeState.stableGeneration
                && unstableGeneration == treeState.unstableGeneration
                && rootId == treeState.rootId
                && rootGeneration == treeState.rootGeneration
                && lastId == treeState.lastId
                && freeListWritePageId == treeState.freeListWritePageId
                && freeListReadPageId == treeState.freeListReadPageId
                && freeListWritePos == treeState.freeListWritePos
                && freeListReadPos == treeState.freeListReadPos
                && clean == treeState.clean
                && valid == treeState.valid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                pageId,
                stableGeneration,
                unstableGeneration,
                rootId,
                rootGeneration,
                lastId,
                freeListWritePageId,
                freeListReadPageId,
                freeListWritePos,
                freeListReadPos,
                clean,
                valid);
    }

    public boolean isClean() {
        return clean;
    }
}
