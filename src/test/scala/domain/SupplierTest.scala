package domain

import org.scalatest.FunSuite
import warehouse.domain.Supplier

class SupplierTest extends FunSuite {

  def supplierForTest(supplierId: String): Supplier = Supplier(supplierId)

  test("Create supplier with non empty state and wrong id") {
    val supplier = supplierForTest("sup1")
    val id = "random"
    val action: Either[String, Option[Supplier.SupplierEvt]] =
      Supplier.Create(id).applyTo(supplier)
    assert(action.isLeft)
    assert(action.left.get equals "Supplier creation error: actor already init")
  }

  test("Create supplier with non empty state and same id") {
    val id = "sup1"
    val supplier = supplierForTest(id)
    val action: Either[String, Option[Supplier.SupplierEvt]] =
      Supplier.Create(id).applyTo(supplier)
    assert(action.isRight)
    assert(action.right.get.isEmpty)
  }

  test("Create supplier with valid id") {
    val supplier = Supplier.emptySupplier
    val id = "sup1"
    val action: Either[String, Option[Supplier.SupplierEvt]] =
      Supplier.Create(id).applyTo(supplier)
    assert(action.isRight)
    val event = action.right.get.get
    val applyResult = event.applyTo(supplier)
    assert(applyResult.supplierId equals id)
  }

  // ADD PRODUCT
  test("Add product to invalid supplier") {
    val supplier = supplierForTest("sup1")
    val product = "prod1"
    val action: Either[String, Option[Supplier.SupplierEvt]] =
      Supplier.AddProduct("wrongId", "test", product).applyTo(supplier)
    assert(action.isLeft)
    assert(action.left.get equals "Supplier not found")
  }

  test("Add product") {
    val supplier = supplierForTest("sup1")
    val product = "prod1"
    val action: Either[String, Option[Supplier.SupplierEvt]] =
      Supplier.AddProduct(supplier.supplierId, "test", product).applyTo(supplier)
    assert(action.isRight)
    val event = action.right.get.get
    val applyResult = event.applyTo(supplier)
    assert(applyResult.supplierId equals supplier.supplierId)
  }
}
