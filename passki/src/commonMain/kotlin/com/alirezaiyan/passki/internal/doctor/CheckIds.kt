package com.alirezaiyan.passki.internal.doctor

/**
 * Stable IDs from the shared setup check catalog (`docs/checks/catalog.json`), the same IDs the Gradle
 * plugin's `passkiCheck` and the web dashboard use. A test keeps [all] in sync with the catalog.
 */
internal object CheckIds {
    const val AND_01: String = "AND-01"
    const val AND_02: String = "AND-02"
    const val AND_03: String = "AND-03"
    const val AND_06: String = "AND-06"
    const val IOS_01: String = "IOS-01"
    const val IOS_02: String = "IOS-02"
    const val IOS_03: String = "IOS-03"
    const val IOS_04: String = "IOS-04"
    const val CFG_01: String = "CFG-01"
    const val DEV_01: String = "DEV-01"
    const val DEV_02: String = "DEV-02"
    const val DEV_03: String = "DEV-03"

    /** Every ID the on-device doctor can report. */
    val all: List<String> = listOf(AND_01, AND_02, AND_03, AND_06, IOS_01, IOS_02, IOS_03, IOS_04, CFG_01, DEV_01, DEV_02, DEV_03)
}
