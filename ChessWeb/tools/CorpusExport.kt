// Generates tests/fixtures/kotlin-corpus.json: random games played with the ANDROID app's Kotlin rules engine (chess-core).
// The web test-suite replays the same moves with chess.js + the web game layer and requires identical legal-move sets,
// SAN, check flags and game results at every ply (differential testing of two independent implementations).
//
// How to regenerate (needs the ChessApp project): put this file in a scratch Gradle JVM project that depends on
// ChessApp's :chess-core module, run main() and write its stdout to ChessWeb/tests/fixtures/kotlin-corpus.json.
import com.casualchess.core.ChessGame
import com.casualchess.core.Move
import com.casualchess.core.Position
import kotlin.random.Random

private fun fnv(text: String): String {
    var h = 0x811c9dc5L
    for (b in text.toByteArray(Charsets.UTF_8)) { h = h xor (b.toLong() and 0xff); h = (h * 0x01000193L) and 0xffffffffL }
    return h.toString(16).padStart(8, '0')
}

private val STARTS = listOf(
    Position.START_FEN,
    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
    "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
    "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
    "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
    "4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1",
    "4k3/4p3/8/8/8/8/4P3/4K3 w - - 0 1",
    "8/P6k/8/8/8/8/6p1/K7 w - - 0 1",
    "4k3/8/8/3n4/8/8/3B4/4K3 w - - 0 1",
    "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 90 60",
)

fun main() {
    val sb = StringBuilder()
    sb.append("{\"generator\":\"ChessApp chess-core (Kotlin) random playouts\",\"starts\":[")
    sb.append(STARTS.joinToString(",") { "\"$it\"" })
    sb.append("],\"games\":[")
    var first = true
    for (seed in 0 until 150) {
        val rng = Random(1000 + seed)
        val start = seed % STARTS.size
        var game = ChessGame.new(Position.fromFen(STARTS[start]))
        val moves = ArrayList<String>(); val sans = ArrayList<String>(); val hashes = ArrayList<String>()
        val checks = StringBuilder(); val results = ArrayList<String>()
        hashes += fnv(game.position.legalMoves.map { it.uci }.sorted().joinToString(","))
        while (!game.isOver && moves.size < 100) {
            val m: Move = game.legalMoves[rng.nextInt(game.legalMoves.size)]
            game = game.play(m)
            moves += m.uci; sans += game.records.last().san
            hashes += fnv(game.position.legalMoves.map { it.uci }.sorted().joinToString(","))
            checks.append(if (game.isCheck) '1' else '0')
            results += (game.result?.termination?.name?.lowercase() ?: "-")
        }
        if (!first) sb.append(","); first = false
        sb.append("{\"s\":$start,\"m\":\"${moves.joinToString(" ")}\",\"san\":\"${sans.joinToString(" ")}\",")
        sb.append("\"h\":\"${hashes.joinToString(" ")}\",\"c\":\"$checks\",\"r\":\"${results.joinToString(" ")}\"}")
    }
    sb.append("]}")
    println(sb)
}
