package dev.izumi.appopsnext.appops.testing

import dev.izumi.appopsnext.appops.model.AppOpIdentifier

object AppOpsTestTarget {
    const val PACKAGE_NAME = "dev.izumi.appopsnext.testtarget"

    val runInBackgroundOperation = AppOpIdentifier(
        stableName = "android:run_in_background",
        shellName = "RUN_IN_BACKGROUND",
    )
}
