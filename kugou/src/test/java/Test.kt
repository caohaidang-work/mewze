import com.caohaidang.edward.kugou.KuGou
import com.caohaidang.edward.kugou.KuGou.generateKeyword
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

// Calls the real KuGou servers, so it is flaky on CI (no network guarantees, rate limits). Remove @Ignore to run it by hand.
@Ignore("Hits the live KuGou API; run manually")
class Test {
    @Test
    fun test() = runBlocking {
        val candidates = KuGou.getLyricsCandidate(generateKeyword("千年以後 (After A Thousand Years)", "陳零九"), 285)
        assertTrue(candidates != null)
        assertTrue(KuGou.getLyrics("楊丞琳", "點水", 259).isSuccess)
    }
}
