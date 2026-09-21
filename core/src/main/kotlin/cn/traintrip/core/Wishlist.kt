package cn.traintrip.core

/** Only city identity is saved; a wish never represents current ticket inventory. */
data class WishCity(val cityId:String,val name:String,val province:String,val addedAt:Long)
interface WishlistStore {
    fun read():List<WishCity>
    fun write(items:List<WishCity>)
    /** The read and write must be serialized across all instances sharing the same file. */
    fun update(change:(List<WishCity>)->List<WishCity>):List<WishCity> = synchronized(this) {
        val previous=read()
        val next=change(previous)
        if(next!=previous)write(next)
        next
    }
}
class WishlistRepository(private val store:WishlistStore) {
    var items:List<WishCity> = emptyList(); private set
    private var loaded=false
    private fun ordered(items:List<WishCity>)=items.distinctBy { it.cityId }.sortedByDescending { it.addedAt }
    fun load():List<WishCity> { val read=store.read();items=ordered(read);loaded=true;return items }
    private fun change(transform:(List<WishCity>)->List<WishCity>) {
        check(loaded) { "想去清单尚未读取" }
        val next=store.update { current -> ordered(transform(ordered(current))) }
        items=next
    }
    fun add(cities:List<City>,now:Long) = change { current ->
        val existing=current.map { it.cityId }.toSet()
        val added=cities.distinctBy { it.id }.filter { it.id !in existing }.mapIndexed { index,c -> WishCity(c.id,c.name,c.province.name,now-index) }
        current+added
    }
    fun remove(id:String):WishCity? {
        var removed:WishCity?=null
        change { current -> removed=current.find { it.cityId==id };current.filterNot { it.cityId==id } }
        return removed
    }
    fun toggle(city:City,now:Long):WishCity? {
        var removed:WishCity?=null
        change { current ->
            removed=current.find { it.cityId==city.id }
            if(removed!=null)current.filterNot { it.cityId==city.id }
            else current+WishCity(city.id,city.name,city.province.name,now)
        }
        return removed
    }
    fun restore(record:WishCity) = change { current -> if(current.any { it.cityId==record.cityId })current else current+record }
}

/** Display grouping never rewrites saved city identity or collection timestamps. */
data class WishProvince(val name:String,val cities:List<WishCity>)
fun wishlistGroups(items:List<WishCity>,catalog:StationCatalog):List<WishProvince> {
    val order=catalog.provinces.mapIndexed { index,p -> p.name to index }.toMap()
    return items.sortedByDescending { it.addedAt }
        .groupBy { catalog.byCity[it.cityId]?.province?.name ?: it.province.ifBlank { "其它城市" } }
        .entries.sortedWith(compareBy({order[it.key] ?: Int.MAX_VALUE},{it.key}))
        .map { WishProvince(it.key,it.value) }
}
