package com.blacksingularities.open3dmaze

import com.blacksingularities.open3dmaze.data.Campaign
import com.blacksingularities.open3dmaze.game.Game
import com.blacksingularities.open3dmaze.game.Geo
import com.blacksingularities.open3dmaze.game.MazeGen
import com.blacksingularities.open3dmaze.game.Phase
import com.blacksingularities.open3dmaze.game.T
import com.blacksingularities.open3dmaze.update.Updates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MazeGenTest {
    @Test
    fun everyCampaignLevelIsSolvable() {
        for (i in 0 until Campaign.COUNT) {
            val lv = MazeGen.generate(Campaign.spec(i))
            val d = MazeGen.bfs(lv.tiles, lv.gw, lv.gh, lv.start)
            assertTrue("poziom $i bez drogi do mety", d[lv.goal] > 0)
            for (g in lv.gems) assertTrue("poziom $i: klejnot nieosiągalny", d[g] >= 0)
            assertEquals(lv.portals.size % 2, 0)
        }
    }

    @Test
    fun sameSeedSameMaze() {
        val a = MazeGen.generate(Campaign.spec(30))
        val b = MazeGen.generate(Campaign.spec(30))
        assertTrue(a.tiles.contentEquals(b.tiles))
        assertEquals(a.goal, b.goal)
    }

    @Test
    fun geometryRoundTrips() {
        for (i in 0 until 40) assertEquals(i, Geo.index(Geo.center(i)))
    }

    @Test
    fun ballStaysInsideWalls() {
        val lv = MazeGen.generate(Campaign.spec(5))
        val g = Game(lv)
        repeat(2000) { k ->
            val gx = if ((k / 120) % 2 == 0) 1f else -1f
            val gy = if ((k / 200) % 2 == 0) 1f else -1f
            g.step(1 / 60f, gx, gy)
            if (g.phase == Phase.RUNNING) {
                assertTrue(lv.at(Geo.index(g.x), Geo.index(g.y)) != T.WALL)
            }
        }
    }

    @Test
    fun versionCompare() {
        assertTrue(Updates.newer("1.2.0", "1.1.9"))
        assertFalse(Updates.newer("1.0.0", "1.0.0"))
        assertFalse(Updates.newer("2.0.0", "0.1.0-dev"))
    }
}
