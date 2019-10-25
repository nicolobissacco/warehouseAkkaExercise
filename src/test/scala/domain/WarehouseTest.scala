package domain

import org.scalatest.FunSuite
import warehouse.domain.Warehouse

class WarehouseTest extends FunSuite {

  def warehouseForTest(warehouseId: String, products: List[String] = List.empty[String]): Warehouse = Warehouse(warehouseId, products)

  // CREATE WAREHOUSE
  /*test("Create warehouse with non empty state and another id") {
    val warehouse = warehouseForTest("test")
    val id = "random"
    val action: Either[String, Option[Warehouse.WarehouseEvt]] =
      Warehouse.Create(id).applyTo(warehouse)
    assert(action.isRight)
    val event = action.right.get.get
    val applyResult = event.applyTo(warehouse)
    assert(applyResult.warehouseId != id)
  }*/

  /*test("Create warehouse with non empty state and wrong id") {
    val warehouse = warehouseForTest("test")
    val id = "random"
    val action: Either[String, Option[Warehouse.WarehouseEvt]] =
      Warehouse.Create(id).applyTo(warehouse)
    assert(action.isLeft)
    assert(action.left.get equals "Warehouse creation error: actor already init")
  }*/

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
      Warehouse.AddProduct("wrongId", product).applyTo(warehouse)
    assert(action.isLeft)
    assert(action.left.get equals "Wrong warehouse")
  }

  test("Add product to warehouse with valid id") {
    val warehouse = warehouseForTest("test")
    val product = "prod1"
    val action: Either[String, Option[Warehouse.WarehouseEvt]] =
      Warehouse.AddProduct(warehouse.warehouseId, product).applyTo(warehouse)
    assert(action.isRight)
    val event = action.right.get.get
    val applyResult = event.applyTo(warehouse)
    assert(applyResult.warehouseId equals warehouse.warehouseId)
    assert(applyResult.products.length equals 1)
  }

  // REMOVE PRODUCT
  test("Remove product to warehouse with wrong id") {
    val warehouse = warehouseForTest("test", List("prod1"))
    val product = "prod1"
    val action: Either[String, Option[Warehouse.WarehouseEvt]] =
      Warehouse.RemoveProduct("wrongId", product).applyTo(warehouse)
    assert(action.isLeft)
    assert(action.left.get equals "Wrong warehouse")
  }

  test("Remove product to warehouse with valid id") {
    val warehouse = warehouseForTest("test", List("prod1"))
    val product = "prod1"
    val action: Either[String, Option[Warehouse.WarehouseEvt]] =
      Warehouse.RemoveProduct(warehouse.warehouseId, product).applyTo(warehouse)
    assert(action.isRight)
    val event = action.right.get.get
    val applyResult = event.applyTo(warehouse)
    assert(applyResult.warehouseId equals warehouse.warehouseId)
    assert(applyResult.products.isEmpty)
  }

  test("Remove product to warehouse with valid id but not found") {
    val warehouse = warehouseForTest("test", List("prod1"))
    val product = "prod2"
    val action: Either[String, Option[Warehouse.WarehouseEvt]] =
      Warehouse.RemoveProduct(warehouse.warehouseId, product).applyTo(warehouse)
    assert(action.isLeft)
    assert(action.left.get equals "No product found")
  }
}
