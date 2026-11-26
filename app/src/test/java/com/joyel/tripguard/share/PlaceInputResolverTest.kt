package com.joyel.tripguard.share

import com.joyel.tripguard.geo.LatLon
import com.joyel.tripguard.search.PlaceResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SHORT_URL = "https://maps.app.goo.gl/8n1Re7AfzJGhUWDy9"

private class FakeGeocodeProvider(
    private val searchResults: Map<String, List<PlaceResult>> = emptyMap(),
    private val reverseResults: Map<String, String> = emptyMap(),
    private val shouldNotBeCalled: Boolean = false
) : GeocodeProvider {
    var searchCallCount = 0
    override suspend fun search(query: String, near: LatLon?, countryCode: String?): List<PlaceResult> {
        if (shouldNotBeCalled) throw AssertionError("geocode.search must not be called for an offline-resolvable input")
        searchCallCount++
        return searchResults[query] ?: emptyList()
    }

    override suspend fun reverse(lat: Double, lon: Double): String? =
        reverseResults["%.4f,%.4f".format(lat, lon)]
}

/** A short-link resolver that waits [delayMs] (virtual time under `runTest`) then returns
 * [result], or throws if [shouldNotBeCalled] — used to prove the loser of a race gets cancelled
 * and the disabled resolver is never invoked at all. */
private class FakeShortLinkResolver(
    private val delayMs: Long,
    private val result: ShortLinkResult?,
    private val shouldNotBeCalled: Boolean = false
) : ShortLinkResolver {
    var callCount = 0
    var cancelled = false
    override suspend fun resolve(url: String): ShortLinkResult? {
        if (shouldNotBeCalled) throw AssertionError("this resolver must not be called")
        callCount++
        try {
            delay(delayMs)
        } catch (e: Throwable) {
            cancelled = true
            throw e
        }
        return result
    }
}

private class FakeLinkCache(private val preloaded: Map<String, Place> = emptyMap()) : LinkCache {
    var getCallCount = 0
    var putCalls = mutableListOf<String>()
    override suspend fun get(url: String): Place? {
        getCallCount++
        return preloaded[url]
    }
    override suspend fun put(url: String, place: Place) {
        putCalls += url
    }
}

class PlaceInputResolverTest {

    @Test
    fun `offline parse returns without any network call`() = runTest {
        val geocode = FakeGeocodeProvider(shouldNotBeCalled = true)
        val local = FakeShortLinkResolver(0, null, shouldNotBeCalled = true)
        val online = FakeShortLinkResolver(0, null, shouldNotBeCalled = true)
        val resolver = PlaceInputResolver(geocode = geocode, localResolver = local, onlineResolver = online)

        val states = resolver.resolve("geo:12.9716,77.5946").toList()
        val found = states.filterIsInstance<ResolveState.Found>().first()
        assertEquals(12.9716, found.place.lat, 0.0001)
        assertEquals(0, local.callCount)
        assertEquals(0, online.callCount)
    }

    @Test
    fun `race returns the faster valid result and cancels the loser`() = runTest {
        val fast = FakeShortLinkResolver(100, ShortLinkResult(lat = 9.98, lon = 76.29, name = "Fast Place"))
        val slow = FakeShortLinkResolver(3000, ShortLinkResult(lat = 1.0, lon = 1.0, name = "Slow Place"))
        val resolver = PlaceInputResolver(localResolver = fast, onlineResolver = slow)

        val states = resolver.resolve(SHORT_URL).toList()
        val found = states.filterIsInstance<ResolveState.Found>().first()
        assertEquals("Fast Place", found.place.name)
        assertEquals(1, fast.callCount)
        assertEquals(1, slow.callCount)
        assertTrue("the slower loser should have been cancelled", slow.cancelled)
    }

    @Test
    fun `first valid wins when the fast one returns null`() = runTest {
        val fastButInvalid = FakeShortLinkResolver(100, null)
        val slowButValid = FakeShortLinkResolver(500, ShortLinkResult(lat = 9.98, lon = 76.29, name = "Valid"))
        val resolver = PlaceInputResolver(localResolver = fastButInvalid, onlineResolver = slowButValid)

        val states = resolver.resolve(SHORT_URL).toList()
        val found = states.filterIsInstance<ResolveState.Found>().first()
        assertEquals("Valid", found.place.name)
    }

    @Test
    fun `both resolvers failing falls back to the shared text's own name`() = runTest {
        val geocode = FakeGeocodeProvider(
            searchResults = mapOf("Cafe Noir" to listOf(PlaceResult("Cafe Noir", LatLon(13.0, 77.6))))
        )
        val local = FakeShortLinkResolver(50, null)
        val online = FakeShortLinkResolver(50, null)
        val resolver = PlaceInputResolver(geocode = geocode, localResolver = local, onlineResolver = online)

        val states = resolver.resolve("Cafe Noir\n$SHORT_URL").toList()
        val found = states.filterIsInstance<ResolveState.Found>().first()
        assertEquals("Cafe Noir", found.place.name)
        assertEquals(13.0, found.place.lat, 0.0001)
    }

    @Test
    fun `raw URL never reaches the field when nothing resolves`() = runTest {
        val local = FakeShortLinkResolver(10, null)
        val online = FakeShortLinkResolver(10, null)
        val resolver = PlaceInputResolver(localResolver = local, onlineResolver = online)

        val states = resolver.resolve(SHORT_URL).toList()
        assertTrue(states.last() is ResolveState.Failed)
        // No Found state ever carried the raw URL as a name (there wasn't a Found at all here).
        assertTrue(states.none { it is ResolveState.Found && (it.place.name == SHORT_URL) })
    }

    @Test
    fun `overall budget times out to Failed instead of hanging`() = runTest {
        val local = FakeShortLinkResolver(10_000, ShortLinkResult(lat = 1.0, lon = 1.0, name = "Too slow"))
        val online = FakeShortLinkResolver(10_000, null)
        val resolver = PlaceInputResolver(localResolver = local, onlineResolver = online)

        val states = resolver.resolve(SHORT_URL).toList()
        assertEquals(ResolveState.Failed, states.last())
    }

    @Test
    fun `cache hit skips the network entirely`() = runTest {
        val cached = Place(9.97, 76.30, "Cached Place")
        val cache = FakeLinkCache(preloaded = mapOf(SHORT_URL to cached))
        val local = FakeShortLinkResolver(0, null, shouldNotBeCalled = true)
        val online = FakeShortLinkResolver(0, null, shouldNotBeCalled = true)
        val resolver = PlaceInputResolver(localResolver = local, onlineResolver = online, linkCache = cache)

        val states = resolver.resolve(SHORT_URL).toList()
        val found = states.filterIsInstance<ResolveState.Found>().first()
        assertEquals("Cached Place", found.place.name)
        assertEquals(1, cache.getCallCount)
    }

    @Test
    fun `online switch OFF means Resolver 2 (HTTP 429 or otherwise) is never invoked`() = runTest {
        val local = FakeShortLinkResolver(10, ShortLinkResult(lat = 9.98, lon = 76.29, name = "Local Only"))
        val online = FakeShortLinkResolver(0, null, shouldNotBeCalled = true)
        val resolver = PlaceInputResolver(
            localResolver = local,
            onlineResolver = online,
            onlineResolverEnabled = { false }
        )

        val states = resolver.resolve(SHORT_URL).toList()
        val found = states.filterIsInstance<ResolveState.Found>().first()
        assertEquals("Local Only", found.place.name)
        assertEquals(0, online.callCount)
    }

    @Test
    fun `blank input fails immediately`() = runTest {
        val resolver = PlaceInputResolver()
        assertEquals(listOf(ResolveState.Failed), resolver.resolve("").toList())
        assertEquals(listOf(ResolveState.Failed), resolver.resolve(null).toList())
    }

    @Test
    fun `a coordinate-only result gets reverse-geocoded in the background`() = runTest {
        val geocode = FakeGeocodeProvider(reverseResults = mapOf("12.9716,77.5946" to "Bengaluru, Karnataka"))
        val resolver = PlaceInputResolver(geocode = geocode)

        val states = resolver.resolve("12.9716, 77.5946").toList()
        val founds = states.filterIsInstance<ResolveState.Found>()
        assertTrue(founds.first().place.isCoordinateLabel)
        assertEquals("Bengaluru, Karnataka", founds.last().place.name)
        assertFalse(founds.last().place.isCoordinateLabel)
    }
}
