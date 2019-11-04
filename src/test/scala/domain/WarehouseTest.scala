package domain

import org.scalatest.FunSuite
import warehouse.domain.{Product, Warehouse}

class WarehouseTest extends FunSuite {

  def warehouseForTest(warehouseId: String, products: List[Product] = List.empty[Product]): Warehouse = Warehouse(warehouseId, products)

  test("Create warehouse with non empty state and wrong id") {
    val warehouse = warehouseForTest("test")
    val id = "random"
    val action: Either[String, Option[Warehouse.WarehouseEvt]] =
      Warehouse.Create(id).applyTo(warehouse)
    assert(action.isLeft)
    assert(action.left.get equals "Warehouse creation error: actor already init")
  }

  test("Create warehouse with non empty state and same id") {
    val id = "test"
    val warehouse = warehouseForTest(id)
    val action: Either[String, Option[Warehouse.WarehouseEvt]] =
      Warehouse.Create(id).applyTo(warehouse)
    assert(action.isRight)
    assert(action.right.get.isEmpty)
  }

  test("Create warehouse with valid id") {
    val warehouse = Warehouse.emptyWarehouse
    val id = "test"
    val action: Either[String, Option[Warehouse.WarehouseEvt]] =
      Warehouse.Create(id).applyTo(warehouse)
    assert(action.isRight)
    val event = action.right.get.get
    val applyResult = event.applyTo(warehouse)
    assert(applyResult.warehouseId equals id)
  }

  // ADD PRODUCT
  test("Add product to warehouse with wrong id") {
    val warehouse = warehouseForTest("test")
    val product = "prod1"
    val action: Either[String, Option[Warehouse.WarehouseEvt]] =
      Warehouse.AddProduct("wrongId", "sup1", product).applyTo(warehouse)
    assert(action.isLeft)
    assert(action.left.get equals "Wrong warehouse")
  }

  test("Add product to warehouse with valid id") {
    val warehouse = warehouseForTest("test")
    val product = "prod1"
    val action: Either[String, Option[Warehouse.WarehouseEvt]] =
      Warehouse.AddProduct(warehouse.warehouseId, "sup1", product).applyTo(warehouse)
    assert(action.isRight)
    val event = action.right.get.get
    val applyResult = event.applyTo(warehouse)
    assert(applyResult.warehouseId equals warehouse.warehouseId)
    assert(applyResult.products.length equals 1)
  }

  test("Add product to warehouse with valid id, same supplier and same product") {
    val warehouse = warehouseForTest("test", List(Product("prod1", "sup1")))
    val product = "prod1"
    val action: Either[String, Option[Warehouse.WarehouseEvt]] =
      Warehouse.AddProduct(warehouse.warehouseId, "sup1", product).applyTo(warehouse)
    assert(action.isLeft)
    assert(action.left.get equals "Product already in warehouse for supplier")
  }

  test("Add product to warehouse with valid id, same supplier but different product") {
    val warehouse = warehouseForTest("test", List(Product("prod1", "sup1")))
    val product = "prod2"
    val action: Either[String, Option[Warehouse.WarehouseEvt]] =
      Warehouse.AddProduct(warehouse.warehouseId, "sup1", product).applyTo(warehouse)
    assert(action.isRight)
    val event = action.right.get.get
    val applyResult = event.applyTo(warehouse)
    assert(applyResult.warehouseId equals warehouse.warehouseId)
    assert(applyResult.products.length equals 2)
  }

  // REMOVE PRODUCT
  test("Remove product from warehouse with wrong id") {
    val warehouse = warehouseForTest("test", List(Product("prod1", "sup1")))
    val product = "prod1"
    val action: Either[String, Option[Warehouse.WarehouseEvt]] =
      Warehouse.RemoveProduct("wrongId", "sup1", product).applyTo(warehouse)
    assert(action.isLeft)
    assert(action.left.get equals "Wrong warehouse")
  }

  test("Remove product from warehouse with invalid supplier id") {
    val warehouse = warehouseForTest("test", List(Product("prod1", "sup1")))
    val product = "prod1"
    val action: Either[String, Option[Warehouse.WarehouseEvt]] =
      Warehouse.RemoveProduct(warehouse.warehouseId, "sup2", product).applyTo(warehouse)
    assert(action.isLeft)
    assert(action.left.get equals "No product found for supplier")
  }

  test("Remove product from warehouse") {
    val warehouse = warehouseForTest("test", List(Product("prod1", "sup1")))
    val product = "prod1"
    val action: Either[String, Option[Warehouse.WarehouseEvt]] =
      Warehouse.RemoveProduct(warehouse.warehouseId, "sup1", product).applyTo(warehouse)
    assert(action.isRight)
    val event = action.right.get.get
    val applyResult = event.applyTo(warehouse)
    assert(applyResult.warehouseId equals warehouse.warehouseId)
    assert(applyResult.products.isEmpty)
  }

  test("Remove product from warehouse with valid id but not found") {
    val warehouse = warehouseForTest("test", List(Product("prod1", "sup1")))
    val product = "prod2"
    val action: Either[String, Option[Warehouse.WarehouseEvt]] =
      Warehouse.RemoveProduct(warehouse.warehouseId, "sup1", product).applyTo(warehouse)
    assert(action.isLeft)
    assert(action.left.get equals "No product found")
  }
}
