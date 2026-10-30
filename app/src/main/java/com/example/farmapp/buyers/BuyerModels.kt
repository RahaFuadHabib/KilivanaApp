package com.example.farmapp.buyers

import com.example.farmapp.buyers.model.*
import com.example.farmapp.buyers.data.DeliveryAddress
import com.example.farmapp.buyer.*
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.orders.*
import java.math.BigDecimal

/** Lossless presentation mapping. The canonical account/cart/catalog remain the only state owners. */
internal fun brianProduct(row:CanonicalFarmerProduct,categories:List<CanonicalCategoryDto>):Product = Product(
    row.productId,row.name,categories.firstOrNull{it.id?.toString()==row.categoryId}?.name ?: "Produce",row.unitPrice,row.unit,
    row.description.orEmpty(),null,null,if(row.availableQuantity.signum()>0 && row.status==CanonicalProductStatus.ACTIVE) StockStatus.InStock else StockStatus.OutOfStock,
    row.minimumOrderQuantity,imageUrl=(row.images.firstOrNull{it.isPrimary==true} ?: row.images.firstOrNull())?.url)
internal fun brianOrder(row:BuyerOrderDto,events:List<OrderEventDto> = emptyList()):Order = Order(
    row.id!!.toString(),buyerDate(row.createdAt),when(row.status){"confirmed"->OrderStatus.Confirmed;"in_transit"->OrderStatus.OutForDelivery;"delivered","completed"->OrderStatus.Delivered;"disputed"->OrderStatus.Disputed;"cancelled"->OrderStatus.Cancelled;else->OrderStatus.Pending},
    row.items.orEmpty().map { item -> CartItem(Product(item.productId!!.toString(),item.productName ?: "Product","",item.unitPrice!!,item.unit.orEmpty(),"",null,null,StockStatus.OutOfStock,BigDecimal.ONE),item.quantity!!,item.subtotal) },
    row.subtotal,row.deliveryFee,row.total,row.deliveryAddress ?: row.pickupLocation.orEmpty(),code="Order",
    timeline=events.map{OrderEvent(it.status.orEmpty(),it.note,buyerDate(it.createdAt))},paymentStatus=row.paymentStatus,paymentMethod=row.paymentMethod)
internal fun brianAddress(row:BuyerAddressDto) = DeliveryAddress(row.id!!,row.userId!!,row.addressText!!,row.latitude,row.longitude,row.label?.takeIf{it.isNotBlank()} ?: "Delivery address")


