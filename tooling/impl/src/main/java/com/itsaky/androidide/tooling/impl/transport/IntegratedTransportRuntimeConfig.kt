package com.itsaky.androidide.tooling.impl.transport

/**
 * Runtime switches for integrated transport stack (AIDL + gRPC(UDS) + REAPI).
 */
data class IntegratedTransportRuntimeConfig(
    val reapiEnabled: Boolean,
    val reapiEndpoint: String,
    val reapiInstanceName: String,
) {
  companion object {
    const val PROP_REAPI_ENABLED = "androidide.tooling.integrated.reapi.enabled"
    const val PROP_REAPI_ENDPOINT = "androidide.tooling.integrated.reapi.endpoint"
    const val PROP_REAPI_INSTANCE = "androidide.tooling.integrated.reapi.instance"

    @JvmStatic
    fun fromSystemProperties(): IntegratedTransportRuntimeConfig {
      val enabled = System.getProperty(PROP_REAPI_ENABLED, "false").toBoolean()
      val endpoint = System.getProperty(PROP_REAPI_ENDPOINT, "").trim()
      val instance = System.getProperty(PROP_REAPI_INSTANCE, "androidide").trim()
      return IntegratedTransportRuntimeConfig(enabled, endpoint, instance)
    }
  }
}
