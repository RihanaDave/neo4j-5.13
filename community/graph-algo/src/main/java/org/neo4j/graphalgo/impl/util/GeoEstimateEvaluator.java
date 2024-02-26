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
package org.neo4j.graphalgo.impl.util;

import org.neo4j.graphalgo.EstimateEvaluator;
import org.neo4j.graphdb.Node;

public class GeoEstimateEvaluator implements EstimateEvaluator<Double> {
    private static final double EARTH_RADIUS = 6371 * 1000; // Meters

    private Node cachedGoal;
    private double[] cachedGoalCoordinates;
    private final String latitudePropertyKey;
    private final String longitudePropertyKey;

    public GeoEstimateEvaluator(String latitudePropertyKey, String longitudePropertyKey) {
        this.latitudePropertyKey = latitudePropertyKey;
        this.longitudePropertyKey = longitudePropertyKey;
    }

    @Override
    public Double getCost(Node node, Node goal) {
        double[] nodeCoordinates = getCoordinates(node);
        if (cachedGoal == null || !cachedGoal.equals(goal)) {
            cachedGoalCoordinates = getCoordinates(goal);
            cachedGoal = goal;
        }
        return distance(nodeCoordinates[0], nodeCoordinates[1], cachedGoalCoordinates[0], cachedGoalCoordinates[1]);
    }

    private double[] getCoordinates(Node node) {
        return new double[] {
            ((Number) node.getProperty(latitudePropertyKey)).doubleValue(),
            ((Number) node.getProperty(longitudePropertyKey)).doubleValue()
        };
    }

    private static double distance(double latitude1, double longitude1, double latitude2, double longitude2) {
        latitude1 = Math.toRadians(latitude1);
        longitude1 = Math.toRadians(longitude1);
        latitude2 = Math.toRadians(latitude2);
        longitude2 = Math.toRadians(longitude2);
        double cLa1 = Math.cos(latitude1);
        double xA = EARTH_RADIUS * cLa1 * Math.cos(longitude1);
        double yA = EARTH_RADIUS * cLa1 * Math.sin(longitude1);
        double zA = EARTH_RADIUS * Math.sin(latitude1);
        double cLa2 = Math.cos(latitude2);
        double xB = EARTH_RADIUS * cLa2 * Math.cos(longitude2);
        double yB = EARTH_RADIUS * cLa2 * Math.sin(longitude2);
        double zB = EARTH_RADIUS * Math.sin(latitude2);
        return Math.sqrt((xA - xB) * (xA - xB) + (yA - yB) * (yA - yB) + (zA - zB) * (zA - zB));
    }
}
