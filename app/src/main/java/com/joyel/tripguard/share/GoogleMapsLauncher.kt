package com.joyel.tripguard.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

private const val MAPS_PACKAGE = "com.google.android.apps.maps"

/** Opens Google Maps (the app, falling back to the browser) with an empty search box, so the
 * user can search a place there and Share it back to Knock Me. */
fun openGoogleMapsPicker(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).apply {
        setPackage(MAPS_PACKAGE)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps")))
        }
    }
}

/** View-only: opens a pin at [lat]/[lon] in whatever app handles geo: links. Silently does
 * nothing if there's no such app. */
fun viewLocationInGoogleMaps(context: Context, lat: Double, lon: Double, label: String) {
    val uri = Uri.parse("geo:$lat,$lon?q=${Uri.encode("$lat,$lon($label)")}")
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}

/** Opens a raw URL (e.g. a short link that couldn't be resolved) in whatever app handles it.
 * Silently does nothing if none can. */
fun openUrlExternally(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
