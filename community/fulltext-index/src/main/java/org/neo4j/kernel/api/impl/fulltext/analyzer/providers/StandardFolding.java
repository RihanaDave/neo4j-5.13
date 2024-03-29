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
package org.neo4j.kernel.api.impl.fulltext.analyzer.providers;

import org.apache.lucene.analysis.Analyzer;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.graphdb.schema.AnalyzerProvider;
import org.neo4j.kernel.api.impl.fulltext.analyzer.StandardFoldingAnalyzer;

@ServiceProvider
public class StandardFolding extends AnalyzerProvider {
    public static final String STANDARD_FOLDING_ANALYZER_NAME = "standard-folding";

    public StandardFolding() {
        super(STANDARD_FOLDING_ANALYZER_NAME);
    }

    @Override
    public Analyzer createAnalyzer() {
        return new StandardFoldingAnalyzer();
    }

    @Override
    public String description() {
        return "Analyzer that uses ASCIIFoldingFilter to remove accents (diacritics). Otherwise behaves as standard "
                + "english analyzer. Note! This Analyser may have unexpected behaviour, such as tokenizing, "
                + "for all non ASCII numbers and symbols.";
    }
}
