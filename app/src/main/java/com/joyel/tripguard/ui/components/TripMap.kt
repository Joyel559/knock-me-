package com.joyel.tripguard.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.joyel.tripguard.geo.LatLon
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

/** Small black-and-white OSM map: destination pin, optional route line, optional radius circle. */
@Composable
fun TripMap(
    modifier: Modifier = Modifier,
    center: LatLon,
    destination: LatLon? = null,
    radiusMeters: Double? = null,
    route: List<LatLon>? = null,
    currentLocation: LatLon? = null,
    currentLocationAccuracyMeters: Double? = null,
    onLongPress: ((LatLon) -> Unit)? = null
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(13.0)
            }
        },
        update = { mapView ->
            mapView.overlays.clear()
            mapView.controller.setCenter(GeoPoint(center.lat, center.lon))

            if (onLongPress != null) {
                val receiver = object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?) = false
                    override fun longPressHelper(p: GeoPoint?): Boolean {
                        p?.let { onLongPress(LatLon(it.latitude, it.longitude)) }
                        return true
                    }
                }
                mapView.overlays.add(MapEventsOverlay(receiver))
            }

            destination?.let { dest ->
                mapView.overlays.add(Marker(mapView).apply {
                    position = GeoPoint(dest.lat, dest.lon)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Destination"
                })

                if (radiusMeters != null) {
                    val circlePoints = (0..360 step 10).map { deg ->
                        destinationOffset(dest, radiusMeters, deg.toDouble())
                    }
                    mapView.overlays.add(Polygon(mapView).apply {
                        points = circlePoints.map { GeoPoint(it.lat, it.lon) }
                        fillPaint.alpha = 0
                        outlinePaint.color = android.graphics.Color.WHITE
                        outlinePaint.strokeWidth = 2f
                    })
                }
            }

            route?.let { pts ->
                mapView.overlays.add(Polyline(mapView).apply {
                    setPoints(pts.map { GeoPoint(it.lat, it.lon) })
                    outlinePaint.color = android.graphics.Color.WHITE
                    outlinePaint.strokeWidth = 5f
                })
            }

            currentLocation?.let { loc ->
                mapView.overlays.add(Marker(mapView).apply {
                    position = GeoPoint(loc.lat, loc.lon)
                    title = "You"
                })
                if (currentLocationAccuracyMeters != null) {
                    val circlePoints = (0..360 step 20).map { deg ->
                        destinationOffset(loc, currentLocationAccuracyMeters, deg.toDouble())
                    }
                    mapView.overlays.add(Polygon(mapView).apply {
                        points = circlePoints.map { GeoPoint(it.lat, it.lon) }
                        fillPaint.alpha = 0
                        outlinePaint.color = android.graphics.Color.GRAY
                        outlinePaint.strokeWidth = 1.5f
                    })
                }
            }

            mapView.invalidate()
        }
    )
}

private fun destinationOffset(center: LatLon, distanceMeters: Double, bearingDeg: Double): LatLon {
    val earthRadius = 6371000.0
    val bearing = Math.toRadians(bearingDeg)
    val lat1 = Math.toRadians(center.lat)
    val lon1 = Math.toRadians(center.lon)
    val angularDistance = distanceMeters / earthRadius

    val lat2 = Math.asin(
        Math.sin(lat1) * Math.cos(angularDistance) +
            Math.cos(lat1) * Math.sin(angularDistance) * Math.cos(bearing)
    )
    val lon2 = lon1 + Math.atan2(
        Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(lat1),
        Math.cos(angularDistance) - Math.sin(lat1) * Math.sin(lat2)
    )
    return LatLon(Math.toDegrees(lat2), Math.toDegrees(lon2))
}
