package cn.traintrip.core

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class WishlistTest {
    private class Store:WishlistStore {
        var items=emptyList<WishCity>();var fail=false;var failWrite=false
        override fun read():List<WishCity> { if(fail) throw IOException();return items }
        override fun write(items:List<WishCity>) { if(fail || failWrite) throw IOException();this.items=items }
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
    @Test fun olderRepositoryDoesNotOverwriteCitiesAddedByAnotherPage() {
        val store=Store()
        val original=WishlistRepository(store);original.load()
        val another=WishlistRepository(store);another.load()
        another.add(listOf(a),100)
        original.add(listOf(b),200)
        assertEquals(listOf(b.id,a.id),WishlistRepository(store).load().map { it.cityId })
        another.remove(a.id)
        assertEquals(listOf(b.id),WishlistRepository(store).load().map { it.cityId })
    }
    @Test fun toggleAndUndoUseLatestSavedCities() {
        val store=Store()
        val original=WishlistRepository(store);original.load()
        val another=WishlistRepository(store);another.load()
        another.add(listOf(a),100)
        val removed=original.toggle(a,200)!!
        assertEquals(a.id,removed.cityId)
        assertTrue(store.items.isEmpty())
        another.add(listOf(b),300)
        original.restore(removed)
        assertEquals(listOf(b.id,a.id),store.items.map { it.cityId })
        another.toggle(a,400)
        original.toggle(a,500)
        assertEquals(listOf(a.id,b.id),store.items.map { it.cityId })
        assertEquals(500L,store.items.first().addedAt)
    }
    @Test fun writeFailureKeepsSavedAndInMemoryCitiesUnchanged() {
        val store=Store();val repo=WishlistRepository(store);repo.load();repo.add(listOf(a),100)
        val before=store.items
        store.failWrite=true
        assertTrue(runCatching { repo.add(listOf(b),200) }.isFailure)
        assertEquals(before,store.items)
        assertEquals(before,repo.items)
        store.failWrite=false
        repo.add(listOf(b),200)
        assertEquals(listOf(b.id,a.id),store.items.map { it.cityId })
    }
}
