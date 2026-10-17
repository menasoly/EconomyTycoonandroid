package app.many.economytycoon.urban

import app.many.economytycoon.core.isqrt

/**
 * The shape of a city plan: a square grid of cells cut into square districts. Districts are numbered row by row. A cell
 * is [cellAreaSqm] square metres, which grows with the size of the city so that a bigger city covers more ground.
 */
class CityGeometry(val gridSize: Int, val blockSize: Int, val cellAreaSqm: Long) {
    init {
        require(gridSize > 0 && blockSize > 0 && gridSize % blockSize == 0) { "grid must be a whole number of districts" }
        require(cellAreaSqm > 0) { "cells need an area" }
    }

    val districtsPerSide: Int get() = gridSize / blockSize
    val districtCount: Int get() = districtsPerSide * districtsPerSide
    val cellsPerDistrict: Int get() = blockSize * blockSize
    val districtAreaSqm: Long get() = cellAreaSqm * cellsPerDistrict

    /** Edge length of a cell in metres, rounded down. */
    val cellMeters: Long get() = isqrt(cellAreaSqm).coerceAtLeast(1L)

    fun districtOfCell(x: Int, y: Int): Int {
        require(x in 0 until gridSize && y in 0 until gridSize) { "cell $x,$y outside the city" }
        return (y / blockSize) * districtsPerSide + x / blockSize
    }

    fun districtOfCell(c: Cell) = districtOfCell(c.x, c.y)

    fun cellsOf(district: Int): List<Cell> {
        require(district in 0 until districtCount) { "no district $district" }
        val x0 = (district % districtsPerSide) * blockSize
        val y0 = (district / districtsPerSide) * blockSize
        val out = ArrayList<Cell>(cellsPerDistrict)
        for (y in y0 until y0 + blockSize) for (x in x0 until x0 + blockSize) out += Cell(x, y)
        return out
    }

    /** The cell nearest the middle of the district (the upper-left of the two middle cells for even blocks). */
    fun centre(district: Int): Cell {
        val x0 = (district % districtsPerSide) * blockSize
        val y0 = (district / districtsPerSide) * blockSize
        return Cell(x0 + (blockSize - 1) / 2, y0 + (blockSize - 1) / 2)
    }

    /** Districts that share an edge with [district], in ascending order. */
    fun neighbours(district: Int): List<Int> {
        val dx = district % districtsPerSide
        val dy = district / districtsPerSide
        val out = ArrayList<Int>(4)
        if (dy > 0) out += district - districtsPerSide
        if (dx > 0) out += district - 1
        if (dx < districtsPerSide - 1) out += district + 1
        if (dy < districtsPerSide - 1) out += district + districtsPerSide
        return out
    }

    /** Distance between district centres along the street grid, in metres. */
    fun distanceMeters(a: Int, b: Int): Long {
        val ca = centre(a)
        val cb = centre(b)
        return ca.manhattan(cb).toLong() * cellMeters
    }

    /** Distance from a cell to the centre of the city, in cells. */
    fun cellsFromCentre(c: Cell): Int {
        val mid = (gridSize - 1) / 2
        return Math.abs(c.x - mid) + Math.abs(c.y - mid)
    }

    /** Index of the district that holds the middle of the city. */
    fun centralDistrict(): Int = districtOfCell((gridSize - 1) / 2, (gridSize - 1) / 2)

    companion object {
        /**
         * Chooses the cell size so that the zones, built to about 85 percent of their default floor-area ratio, house the
         * population with 20 percent to spare; never below 2,000 square metres.
         */
        fun forLayout(population: Long, zones: List<ZoneUse>, params: UrbanParams): CityGeometry {
            // Residential floor area per square metre of cell, in millionths, summed over the districts.
            var perCell = 0L
            for (z in zones) perCell += z.residentialShare.toLong() * z.defaultFarMilli
            perCell *= params.blockSize.toLong() * params.blockSize
            require(perCell > 0L) { "a city needs residential floor area" }
            // perCell is in units of 1e-6 floor sqm per cell sqm; 85 percent of it is built.
            val wanted = population.coerceAtLeast(1L) * params.sqmPerResident * 12L / 10L
            val cellArea = wanted * 1_000_000L / (perCell * 85L / 100L)
            return CityGeometry(params.gridSize, params.blockSize, cellArea.coerceAtLeast(2_000L))
        }
    }
}
