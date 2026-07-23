package dev.izumi.appops.appops.testing

import dev.izumi.appops.appops.model.AppOpIdentifier

object AppOpsTestTarget {
    const val PACKAGE_NAME = "dev.izumi.appops.testtarget"

    val runInBackgroundOperation = AppOpIdentifier(
        stableName = "android:run_in_background",
        shellName = "RUN_IN_BACKGROUND",
    )
}
