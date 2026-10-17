package com.nuvio.tv.data.mediaserver.mediabrowser

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class UniversalPlaybackRequestTest {
    private val json = MediaBrowserClient("X-Emby-Authorization", OkHttpClient(),
        ServerClientIdentity("Test TV", "test", { "test-device" })).json

    private fun request(limit: Long?) = PlaybackInfoRequest(
        userId = "test-user", mediaSourceId = "test-source", maxStreamingBitrate = limit,
        deviceProfile = DeviceProfile("Kino", limit, emptyList(), emptyList(), emptyList())
    )

    @Test fun unlimitedOmitsBothServerAndDeviceProfileBitrateFields() {
        val body = json.parseToJsonElement(json.encodeToString(request(null))).jsonObject
        assertFalse(body.containsKey("MaxStreamingBitrate"))
        assertFalse(body.getValue("DeviceProfile").jsonObject.containsKey("MaxStreamingBitrate"))
    }

    @Test fun explicitLimitIsSentInBothPlaces() {
        val body = json.parseToJsonElement(json.encodeToString(request(20_000_000))).jsonObject
        assertEquals("20000000", body.getValue("MaxStreamingBitrate").toString())
        assertEquals("20000000", body.getValue("DeviceProfile").jsonObject.getValue("MaxStreamingBitrate").toString())
    }
}
