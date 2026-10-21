package app.cellscope

import android.content.Context
import cz.mroczis.netmonster.core.factory.NetMonsterFactory
import cz.mroczis.netmonster.core.model.cell.CellGsm
import cz.mroczis.netmonster.core.model.cell.CellLte
import cz.mroczis.netmonster.core.model.cell.CellNr
import cz.mroczis.netmonster.core.model.cell.CellTdscdma
import cz.mroczis.netmonster.core.model.cell.CellWcdma
import cz.mroczis.netmonster.core.model.cell.ICell
import cz.mroczis.netmonster.core.model.connection.PrimaryConnection

/** Одна сота в удобном для UI виде. */
data class CellRow(
    val tech: String,                 // "2G", "3G", "4G", "5G"
    val serving: Boolean,
    val operator: String?,            // "250 01"
    val band: String?,                // "2100"
    val channel: Int?,                // ARFCN / UARFCN / EARFCN / NRARFCN
    val pci: Int?,                    // PSC (3G) / PCI (4G, 5G) / BSIC (2G)
    val dbm: Int?,                    // основной уровень сигнала
    val details: List<Pair<String, String>>, // CI, CID, RNC, LAC, RSSI, RSCP, ...
)

data class Snapshot(
    val time: Long,
    val cells: List<CellRow>,
) {
    val serving: CellRow? get() = cells.firstOrNull { it.serving }
}

class CellRepository(context: Context) {
    private val netMonster = NetMonsterFactory.get(context.applicationContext)

    /** Требует разрешений ACCESS_FINE_LOCATION и READ_PHONE_STATE. */
    fun read(): Snapshot {
        val cells = try {
            netMonster.getCells().map { it.toRow() }
        } catch (e: SecurityException) {
            emptyList()
        }
        // Основные соты сверху, дальше по убыванию сигнала
        val sorted = cells.sortedWith(
            compareByDescending<CellRow> { it.serving }.thenByDescending { it.dbm ?: Int.MIN_VALUE }
        )
        return Snapshot(System.currentTimeMillis(), sorted)
    }

    private fun ICell.toRow(): CellRow {
        val isServing = connectionStatus is PrimaryConnection
        val plmn = network?.let { "${it.mcc} ${it.mnc}" }
        val bandName = band?.name
        val channel = band?.channelNumber

        return when (this) {
            is CellGsm -> CellRow(
                tech = "2G", serving = isServing, operator = plmn, band = bandName,
                channel = channel, pci = bsic, dbm = signal.rssi,
                details = listOfNotNull(
                    "CID" to cid.s(), "LAC" to lac.s(), "BSIC" to bsic.s(),
                    "ARFCN" to channel.s(), "RSSI" to signal.rssi.dbm(),
                ),
            )
            is CellWcdma -> CellRow(
                tech = "3G", serving = isServing, operator = plmn, band = bandName,
                channel = channel, pci = psc, dbm = signal.rscp ?: signal.rssi,
                details = listOfNotNull(
                    "CI" to ci.s(), "CID" to cid.s(), "RNC" to rnc.s(), "LAC" to lac.s(),
                    "PSC" to psc.s(), "UARFCN" to channel.s(),
                    "RSSI" to signal.rssi.dbm(), "RSCP" to signal.rscp.dbm(),
                    "Ec/No" to (signal.ecno?.let { "$it дБ" } ?: "—"),
                ),
            )
            is CellTdscdma -> CellRow(
                tech = "3G", serving = isServing, operator = plmn, band = bandName,
                channel = channel, pci = cpid, dbm = signal.rscp,
                details = listOfNotNull(
                    "CI" to ci.s(), "LAC" to lac.s(), "CPID" to cpid.s(), "RSCP" to signal.rscp.dbm(),
                ),
            )
            is CellLte -> CellRow(
                tech = "4G", serving = isServing, operator = plmn, band = bandName,
                channel = channel, pci = pci, dbm = signal.rsrp,
                details = listOfNotNull(
                    "ECI" to eci.s(), "eNB" to eNb.s(), "CID" to cid.s(), "TAC" to tac.s(),
                    "PCI" to pci.s(), "EARFCN" to channel.s(),
                    "RSRP" to signal.rsrp.dbm(), "RSRQ" to (signal.rsrq?.let { "$it дБ" } ?: "—"),
                    "SNR" to (signal.snr?.let { "$it дБ" } ?: "—"),
                ),
            )
            is CellNr -> CellRow(
                tech = "5G", serving = isServing, operator = plmn, band = bandName,
                channel = channel, pci = pci, dbm = signal.ssRsrp,
                details = listOfNotNull(
                    "NCI" to nci.s(), "TAC" to tac.s(), "PCI" to pci.s(), "NRARFCN" to channel.s(),
                    "SS-RSRP" to signal.ssRsrp.dbm(),
                    "SS-RSRQ" to (signal.ssRsrq?.let { "$it дБ" } ?: "—"),
                    "SS-SINR" to (signal.ssSinr?.let { "$it дБ" } ?: "—"),
                ),
            )
            else -> CellRow("?", isServing, plmn, bandName, channel, null, null, emptyList())
        }
    }

    private fun Any?.s(): String = this?.toString() ?: "—"
    private fun Int?.dbm(): String = this?.let { "$it дБм" } ?: "—"
}
