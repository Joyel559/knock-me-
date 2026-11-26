package com.joyel.tripguard.share

/** Test seam: the resolve outcome now lives in `TripSetupScreen`'s Compose-local state, which an
 * androidTest can't read without a Compose-test dependency (deliberately not added — see
 * docs/TEST-PLAN-SHARE.md). `TripSetupScreen.applyPlaceTo` mirrors every applied place here too,
 * so `ShareToTripSetupTest` has something observable to poll, the same role
 * `SharedDestinationHolder` played before v2.0 moved resolution out of it. Negligible production
 * cost (two nullable var writes), never read outside tests. */
object TripSetupTestState {
    var lastAppliedFrom: Place? = null
    var lastAppliedTo: Place? = null
}
