package app.vehplayer.android.server

/**
 * ARCHITECTURE.md §1: video/audio/input/control are one server, one WS
 * connection. CaptureService (video) and AudioCaptureService (audio) are
 * separate Android Services (different foregroundServiceType requirements,
 * see AndroidManifest.xml's comment on that split) but must broadcast onto
 * the same LocalMediaServer instance rather than each opening their own
 * port. This holder is the seam between them.
 *
 * TODO(claude-code): this is a plain nullable var for Gate-2 simplicity.
 * If service start/stop ordering ever races (audio starting before video
 * has created the server, or vice versa), promote this to something with
 * proper start-order guarantees (e.g. video capture always owns server
 * lifecycle, audio waits on it via a callback/Flow) rather than debugging
 * a null server field in the field.
 */
object ServerHolder {
    @Volatile
    var server: LocalMediaServer? = null
}
