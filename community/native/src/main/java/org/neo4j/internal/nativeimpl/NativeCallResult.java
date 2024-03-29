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
package org.neo4j.internal.nativeimpl;

public class NativeCallResult {
    public static final NativeCallResult SUCCESS = new NativeCallResult();

    private static final String SUCCESS_MESSAGE = "Successful call.";
    private final int errorCode;
    private final String errorMessage;

    private NativeCallResult() {
        this(NativeAccess.SUCCESS, SUCCESS_MESSAGE);
    }

    public NativeCallResult(int errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public boolean isError() {
        return errorCode != NativeAccess.SUCCESS;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "ErrorCode=" + errorCode + ", errorMessage='" + errorMessage + '\'';
    }
}
