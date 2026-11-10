package com.example.farmapp.checkout

import com.example.farmapp.buyer.buyerScope
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal

internal class MemoryCheckoutJournalStorage(var record: StoredCheckoutJournal = StoredCheckoutJournal(0,null)) : CheckoutJournalStorage {
    var writes=0
    var fail=false
    @Synchronized override fun read()=record
    @Synchronized override fun write(expectedVersion:Long,json:String){
        check(record.version==expectedVersion){"Another checkout changed. Reopen checkout before continuing."}
        if(fail)throw IOException("Synthetic persistence failure")
        record=StoredCheckoutJournal(expectedVersion+1,json);writes++
    }
}

class CheckoutJournalStoreTest {
    private val quote="""{"quoteId":"old-quote","addressId":8,"subtotal":3.21,"deliveryFee":150.00,"total":153.21,"currency":"KES","expiresAt":"2026-10-29T12:15:00Z","deliveryMethod":"DOOR_DELIVERY"}"""
    private val pending=CheckoutJournal("UPLOAD_PENDING",mapOf(7L to 3),BigDecimal("3.21"),10)

    @Test fun legacySnapshotsKeepEveryPhaseFieldAndVersionAcrossStoreRecreation(){
        for(phase in listOf("UPLOAD_PENDING","QUANTITY_PENDING","CART_REJECTED","UPLOAD_ACKNOWLEDGED","CHECKOUT_PENDING","CHECKOUT_ACKNOWLEDGED","QUOTE_READY","ORDER_PENDING","ORDER_RETURNED","ORDER_VERIFIED")){
            val order=if(phase in setOf("ORDER_RETURNED","ORDER_VERIFIED"))",\"orderId\":105" else ""
            val json="""{"phase":"$phase","items":{"7":3},"expectedSubtotal":3.21,"cartId":10,"quote":$quote,"request":{"deliveryMethod":"DOOR_DELIVERY","addressId":8},"beforeOrderIds":[90]$order}"""
            val storage=MemoryCheckoutJournalStorage(StoredCheckoutJournal(4,json))
            val store=VersionedCheckoutJournalStore(storage);val restored=store.read()
            assertEquals(phase,restored.phase);assertEquals(mapOf(7L to 3),restored.items)
            assertEquals(4L,storage.record.version)
            store.write(restored)
            assertEquals(5L,storage.record.version)
            assertEquals(JsonParser.parseString(json),JsonParser.parseString(storage.record.json))
            assertEquals(restored,VersionedCheckoutJournalStore(storage).read())
        }
    }

    @Test fun corruptRecordsStayUnresolvedWithoutChangingTheirBytesOrVersion(){
        for(json in listOf("not JSON","{}","""{"phase":"NONE","items":{"7":3,"7":4}}""")){
            val original=StoredCheckoutJournal(8,json);val storage=MemoryCheckoutJournalStorage(original)
            assertEquals("UNRESOLVED",VersionedCheckoutJournalStore(storage).read().phase)
            assertEquals("UNRESOLVED",VersionedCheckoutJournalStore(storage).read().phase)
            assertEquals(original,storage.record);assertEquals(0,storage.writes)
        }
    }

    @Test fun staleWriterCannotOverwriteANewerReservation(){
        val storage=MemoryCheckoutJournalStorage();val first=VersionedCheckoutJournalStore(storage);val stale=VersionedCheckoutJournalStore(storage)
        first.read();stale.read();first.write(pending)
        val saved=storage.record
        assertThrows(IllegalStateException::class.java){stale.write(CheckoutJournal())}
        assertEquals(saved,storage.record);assertEquals(1,storage.writes)
        assertEquals(pending,stale.read());stale.write(pending)
        assertEquals(2L,storage.record.version)
    }

    @Test fun failedCommitDoesNotAdvanceTheReadersVersionOrEraseTheJournal(){
        val storage=MemoryCheckoutJournalStorage();val store=VersionedCheckoutJournalStore(storage);store.read()
        storage.fail=true
        assertThrows(IOException::class.java){store.write(pending)}
        assertEquals(StoredCheckoutJournal(0,null),storage.record)
        storage.fail=false;store.write(pending)
        assertEquals(1L,storage.record.version);assertEquals(pending,VersionedCheckoutJournalStore(storage).read())
    }

    @Test fun writingBeforeReadingOrEncodingInvalidDataDoesNotReachStorage(){
        val storage=MemoryCheckoutJournalStorage();val store=VersionedCheckoutJournalStore(storage)
        assertThrows(IllegalStateException::class.java){store.write(pending)}
        store.read()
        assertThrows(IllegalArgumentException::class.java){store.write(pending.copy(items=mapOf(7L to 0)))}
        assertEquals(0,storage.writes);assertEquals(StoredCheckoutJournal(0,null),storage.record)
    }

    @Test fun independentStorageAndExistingBuyerScopesDoNotCrossAccountsOrOrigins(){
        val firstStorage=MemoryCheckoutJournalStorage();val secondStorage=MemoryCheckoutJournalStorage()
        val first=VersionedCheckoutJournalStore(firstStorage);val second=VersionedCheckoutJournalStore(secondStorage)
        first.read();second.read();first.write(pending)
        assertEquals(CheckoutJournal(),second.read());assertEquals(0L,secondStorage.record.version)
        val scope=buyerScope("https://synthetic.test/api/v1/","42")
        assertEquals("c63a630dcf4ef0895a3917dd84e4a1ae65baf2fc4d47f13c8fb48c79184b1f8a",scope)
        assertNotEquals(scope,buyerScope("https://synthetic.test/api/v1/","99"))
        assertNotEquals(scope,buyerScope("https://other.test/api/v1/","42"))
    }
}
