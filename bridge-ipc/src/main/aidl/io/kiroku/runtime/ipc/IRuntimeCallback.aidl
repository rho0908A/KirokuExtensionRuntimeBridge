package io.kiroku.runtime.ipc;

/**
 * One-way callback channel. Events are JSON encoded (`RuntimeEvent`) so that new event kinds can be
 * introduced without breaking older SDK clients.
 */
oneway interface IRuntimeCallback {
    void onEvent(String eventJson);
}
