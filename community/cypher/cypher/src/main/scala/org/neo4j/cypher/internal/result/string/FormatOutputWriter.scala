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
package org.neo4j.cypher.internal.result.string

import java.io.PrintWriter

trait FormatOutputWriter {
  def print(str: String): Unit
  def println(str: String): Unit
}

case class StringBuilderWrapper(sb: StringBuilder) extends FormatOutputWriter {
  override def print(str: String): Unit = sb ++= str

  override def println(str: String): Unit = {
    sb ++= str
    sb ++= System.lineSeparator()
  }
}

case class PrintWriterWrapper(pw: PrintWriter) extends FormatOutputWriter {
  override def print(str: String): Unit = pw.print(str)
  override def println(str: String): Unit = pw.println(str)
}
