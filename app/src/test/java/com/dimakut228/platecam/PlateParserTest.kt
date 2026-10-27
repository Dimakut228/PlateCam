package com.dimakut228.platecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateParserTest {

    @Test
    fun parsesRussianCivilPlate() {
        val read = PlateParser.parse("A123BC77", 0.9f)
        assertEquals(Country.RU, read?.country)
        assertEquals("A123BC77", read?.canonical)
        assertEquals("А123ВС 77", read?.display)
        assertEquals("легковой", read?.kind)
    }

    @Test
    fun normalizesCyrillic() {
        val read = PlateParser.parse("А123ВС77", 0.9f)
        assertEquals("A123BC77", read?.canonical)
    }

    @Test
    fun correctsLookalikeDigits() {
        val read = PlateParser.parse("A1238C77", 0.9f)
        assertEquals("A123BC77", read?.canonical)
    }

    @Test
    fun parsesRussianTrailer() {
        val read = PlateParser.parse("AB123477", 0.9f)
        assertEquals(Country.RU, read?.country)
        assertEquals("прицеп", read?.kind)
        assertEquals("АВ 1234 77", read?.display)
    }

    @Test
    fun parsesRussianMotorcycle() {
        val read = PlateParser.parse("1234AB77", 0.9f)
        assertEquals(Country.RU, read?.country)
        assertEquals("мото", read?.kind)
    }

    @Test
    fun parsesRussianDiplomatic() {
        val read = PlateParser.parse("D00177", 0.9f)
        assertEquals(Country.RU, read?.country)
        assertEquals("дипломатический", read?.kind)
    }

    @Test
    fun parsesRussianTransit() {
        val read = PlateParser.parse("T123AB77", 0.9f)
        assertEquals(Country.RU, read?.country)
        assertTrue(read?.kind?.contains("транзит") == true)
    }

    @Test
    fun parsesBelarusStandard() {
        val read = PlateParser.parse("1234AB5", 0.9f)
        assertEquals(Country.BY, read?.country)
        assertEquals("1234 АВ-5", read?.display)
    }

    @Test
    fun parsesBelarusElectric() {
        val read = PlateParser.parse("E123AB5", 0.9f)
        assertEquals(Country.BY, read?.country)
        assertEquals("электро", read?.kind)
    }

    @Test
    fun parsesBelarusTruckWithFilter() {
        val read = PlateParser.parse("AB12345", 0.9f, PlateHint(country = Country.BY))
        assertEquals(Country.BY, read?.country)
        assertEquals("грузовой", read?.kind)
    }

    @Test
    fun parsesRussianTaxiWithYellowHint() {
        val read = PlateParser.parse("AB12345", 0.9f, PlateHint(country = Country.RU, yellow = true))
        assertEquals(Country.RU, read?.country)
        assertEquals("такси", read?.kind)
    }

    @Test
    fun marksAmbiguousPlate() {
        val read = PlateParser.parse("AB12345", 0.9f)
        assertEquals(Country.UNKNOWN, read?.country)
    }

    @Test
    fun parsesBelarusTaxi() {
        val read = PlateParser.parse("1TAX2345", 0.9f)
        assertEquals(Country.BY, read?.country)
        assertEquals("такси", read?.kind)
    }

    @Test
    fun parsesBelarusDiplomatic() {
        val read = PlateParser.parse("CD12347", 0.9f, PlateHint(country = Country.BY))
        assertEquals(Country.BY, read?.country)
        assertEquals("дипломатический", read?.kind)
    }

    @Test
    fun findsPlateInsideLargerText() {
        val read = PlateParser.parse("RUSA123BC77", 0.9f)
        assertEquals("A123BC77", read?.canonical)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(PlateParser.parse("XYZ", 0.9f))
    }

    @Test
    fun fallbackKeepsUnknownSpecialPlate() {
        val read = PlateParser.parse("T123477", 0.4f)
        assertTrue(read != null)
    }
}
