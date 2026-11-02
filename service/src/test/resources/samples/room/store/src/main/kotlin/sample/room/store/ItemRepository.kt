package sample.room.store

class ItemRepository(private val database: StoreDatabase) {
    fun load(id: Long): Item? = database.itemDao().find(id)

    fun save(item: Item) {
        database.itemDao().insert(item)
    }
}
