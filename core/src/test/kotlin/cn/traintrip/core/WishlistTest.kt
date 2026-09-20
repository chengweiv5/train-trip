package cn.traintrip.core

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class WishlistTest {
    private class Store:WishlistStore {
        var items=emptyList<WishCity>();var fail=false
        override fun read():List<WishCity> { if(fail) throw IOException();return items }
        override fun write(items:List<WishCity>) { if(fail) throw IOException();this.items=items }
    }
    private val catalog=StationCatalog.bundled()
    private val a=catalog.byCity.getValue("120000")
    private val b=catalog.byCity.getValue("370100")
    @Test fun duplicateAddsAndUndoKeepOriginalOrder() {
        val store=Store();val repo=WishlistRepository(store);repo.load()
        repo.add(listOf(a,b,a),100)
        assertEquals(listOf(a.id,b.id),repo.items.map { it.cityId })
        val removed=repo.remove(a.id)!!
        repo.add(listOf(a),200)
        repo.restore(removed)
        assertEquals(200L,repo.items.first().addedAt)
        repo.remove(a.id);repo.restore(removed)
        assertEquals(listOf(a.id,b.id),repo.items.map { it.cityId })
        assertEquals(repo.items,WishlistRepository(store).load())
    }
    @Test fun failedWritesAndReadsDoNotInventEmptySuccess() {
        val store=Store();val repo=WishlistRepository(store);repo.load();repo.add(listOf(a),100)
        store.fail=true
        assertTrue(runCatching { repo.add(listOf(b),200) }.isFailure)
        assertEquals(listOf(a.id),repo.items.map { it.cityId })
        assertTrue(runCatching { repo.remove(a.id) }.isFailure)
        assertEquals(1,repo.items.size)
        val fresh=WishlistRepository(store)
        assertTrue(runCatching { fresh.load() }.isFailure)
        assertTrue(runCatching { fresh.add(listOf(b),200) }.isFailure)
    }
}
