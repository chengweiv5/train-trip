package cn.traintrip.core

/** Only city identity is saved; a wish never represents current ticket inventory. */
data class WishCity(val cityId:String,val name:String,val province:String,val addedAt:Long)
interface WishlistStore { fun read():List<WishCity>; fun write(items:List<WishCity>) }
class WishlistRepository(private val store:WishlistStore) {
    var items:List<WishCity> = emptyList(); private set
    private var loaded=false
    fun load():List<WishCity> { val read=store.read();items=read.distinctBy { it.cityId }.sortedByDescending { it.addedAt };loaded=true;return items }
    private fun commit(next:List<WishCity>) { check(loaded) { "想去清单尚未读取" };store.write(next);items=next }
    fun add(cities:List<City>,now:Long) {
        val existing=items.map { it.cityId }.toSet()
        val added=cities.distinctBy { it.id }.filter { it.id !in existing }.mapIndexed { index,c -> WishCity(c.id,c.name,c.province.name,now-index) }
        commit((items+added).sortedByDescending { it.addedAt })
    }
    fun remove(id:String):WishCity? {
        val record=items.find { it.cityId==id } ?: return null
        commit(items.filterNot { it.cityId==id });return record
    }
    fun restore(record:WishCity) { if(items.none { it.cityId==record.cityId }) commit((items+record).sortedByDescending { it.addedAt }) }
}
