# Backend Foundation: DocumentPersister Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the document write sequence (numbering + stock movement) out of the EasyAdmin controllers into a tested `DocumentPersister` service that the mobile write API can safely reuse.

**Architecture:** The stock arithmetic for sales invoices and purchase bills currently lives inline in two EasyAdmin controllers. This plan moves it into one service, proves it with tests that assert real `ItemStock` rows, then rewires the controllers to call it. No portal behaviour changes — which is exactly what makes the refactor verifiable.

**Tech Stack:** PHP 8.3, Symfony 7.3, Doctrine ORM, SQLite, PHPUnit (to be installed), EasyAdmin 4.

## Global Constraints

- Money is `decimal(14,2)` in Doctrine, handled as PHP `float` via entity getters. Never round-trip money through a float literal in a test assertion without an explicit delta.
- Quantities are `decimal(14,3)`, handled as `float`.
- `DocumentNumberService::consume()` must be called **exactly once** per new document, and it flushes `Settings`.
- Stock signs are the one asymmetry between document types: **sales removes** stock, **purchase adds** it.
- Stock shortfalls are **warnings, never errors**. A sale is never blocked.
- Existing project code uses `declare(strict_types=1);` and `final` is not currently used on services — follow the existing style (no `final`, `readonly` promoted constructor properties).
- Do not modify entity classes in this plan.

---

## File Structure

| File | Responsibility |
|---|---|
| `phpunit.dist.xml` | PHPUnit config (created by symfony/test-pack) |
| `.env.test` | Points tests at a throwaway SQLite database |
| `tests/DomainTestCase.php` | Base test case: boots kernel, rebuilds schema, seeds baseline rows, exposes services |
| `tests/Service/DocumentPersisterTest.php` | All stock/numbering behaviour tests |
| `src/Service/DocumentPersister.php` | The extracted write sequence |
| `src/Controller/Admin/SalesInvoiceCrudController.php` | Delegates to the service; keeps only flash presentation |
| `src/Controller/Admin/PurchaseBillCrudController.php` | Same |
| `src/Controller/Admin/ReceiptCrudController.php` | Numbering only |
| `src/Controller/Admin/PaymentCrudController.php` | Numbering only |

---

### Task 1: Test infrastructure

**Files:**
- Create: `.env.test`
- Create: `phpunit.dist.xml` (generated)
- Create: `tests/SmokeTest.php`
- Modify: `composer.json` (dev dependencies)

**Interfaces:**
- Consumes: nothing
- Produces: a working `vendor/bin/phpunit`, and a test SQLite database at `var/test.db`

- [ ] **Step 1: Install the test pack**

```bash
composer require --dev symfony/test-pack
```

Expected: installs `phpunit/phpunit`, `symfony/phpunit-bridge`, and generates `phpunit.dist.xml`.

- [ ] **Step 2: Point tests at a throwaway database**

Create `.env.test`:

```
KERNEL_CLASS='App\Kernel'
APP_SECRET='$ecretf0rt3st'
SYMFONY_DEPRECATIONS_HELPER=999999
DATABASE_URL="sqlite:///%kernel.project_dir%/var/test.db"
```

This must be a different file from `var/data.db` — tests drop and recreate the schema on every single test, and pointing that at the development database would destroy real data.

- [ ] **Step 3: Write the smoke test**

Create `tests/SmokeTest.php`:

```php
<?php

declare(strict_types=1);

namespace App\Tests;

use App\Service\DocumentNumberService;
use App\Service\StockService;
use Symfony\Bundle\FrameworkBundle\Test\KernelTestCase;

class SmokeTest extends KernelTestCase
{
    public function testContainerProvidesDomainServices(): void
    {
        self::bootKernel();
        $container = static::getContainer();

        $this->assertInstanceOf(StockService::class, $container->get(StockService::class));
        $this->assertInstanceOf(DocumentNumberService::class, $container->get(DocumentNumberService::class));
    }
}
```

- [ ] **Step 4: Run it and confirm it passes**

Run: `vendor/bin/phpunit tests/SmokeTest.php`
Expected: PASS, 1 test. If services are not public in the test container, add to `config/services_test.yaml`:

```yaml
services:
    App\Service\StockService: { public: true }
    App\Service\DocumentNumberService: { public: true }
    App\Service\DocumentStockManager: { public: true }
    doctrine.orm.default_entity_manager: { alias: 'doctrine.orm.entity_manager', public: true }
```

- [ ] **Step 5: Commit**

```bash
git add composer.json composer.lock phpunit.dist.xml .env.test tests/SmokeTest.php config/services_test.yaml
git commit -m "test: add phpunit infrastructure and isolated test database"
```

---

### Task 2: Domain test case with fixtures

**Files:**
- Create: `tests/DomainTestCase.php`
- Test: itself (Task 3 is its first consumer)

**Interfaces:**
- Consumes: the test kernel from Task 1
- Produces:
  - `DomainTestCase::$em` — `EntityManagerInterface`
  - `DomainTestCase::$plantA`, `$plantB` — `Plant`
  - `DomainTestCase::$customer`, `$supplier` — `Party`
  - `DomainTestCase::$fabric` — `Item`
  - `protected function stockOf(Item $item, Plant $plant): float`
  - `protected function seedStock(Item $item, Plant $plant, float $qty): void`
  - `protected function service(string $class): object`

- [ ] **Step 1: Write the base test case**

Create `tests/DomainTestCase.php`:

```php
<?php

declare(strict_types=1);

namespace App\Tests;

use App\Entity\Item;
use App\Entity\Party;
use App\Entity\Plant;
use App\Entity\Settings;
use App\Service\StockService;
use Doctrine\ORM\EntityManagerInterface;
use Doctrine\ORM\Tools\SchemaTool;
use Symfony\Bundle\FrameworkBundle\Test\KernelTestCase;

abstract class DomainTestCase extends KernelTestCase
{
    protected EntityManagerInterface $em;
    protected Plant $plantA;
    protected Plant $plantB;
    protected Party $customer;
    protected Party $supplier;
    protected Item $fabric;

    protected function setUp(): void
    {
        self::bootKernel();

        $this->em = static::getContainer()->get('doctrine.orm.default_entity_manager');

        // A fresh schema per test: these tests mutate stock, and leakage between
        // them would make failures depend on execution order.
        $tool = new SchemaTool($this->em);
        $metadata = $this->em->getMetadataFactory()->getAllMetadata();
        $tool->dropSchema($metadata);
        $tool->createSchema($metadata);

        $this->plantA = (new Plant())->setName('Plant A');
        $this->plantB = (new Plant())->setName('Plant B');

        $this->customer = (new Party())->setName('Acme Sacks')->setType('customer');
        $this->supplier = (new Party())->setName('Ambica Threads')->setType('supplier');

        $this->fabric = (new Item())
            ->setName('PP Fabric Roll')
            ->setType('raw_material')
            ->setUnit('kg')
            ->setDefaultRate(76.00);

        // Settings is a single row the numbering service reads; without it,
        // consume() has nothing to increment.
        $settings = new Settings();

        foreach ([$this->plantA, $this->plantB, $this->customer, $this->supplier, $this->fabric, $settings] as $entity) {
            $this->em->persist($entity);
        }

        $this->em->flush();
    }

    protected function tearDown(): void
    {
        parent::tearDown();
        $this->em->close();
    }

    protected function service(string $class): object
    {
        return static::getContainer()->get($class);
    }

    protected function stockOf(Item $item, Plant $plant): float
    {
        return $this->service(StockService::class)->getQty($item, $plant);
    }

    protected function seedStock(Item $item, Plant $plant, float $qty): void
    {
        $this->service(StockService::class)->setQty($item, $plant, $qty);
    }
}
```

- [ ] **Step 2: Verify it boots by running the existing smoke test**

Run: `vendor/bin/phpunit`
Expected: PASS. If `Settings` requires constructor arguments or non-null fields, read `src/Entity/Settings.php` and set them here — the defaults on the entity should suffice.

- [ ] **Step 3: Commit**

```bash
git add tests/DomainTestCase.php
git commit -m "test: add domain test case with schema rebuild and baseline fixtures"
```

---

### Task 3: Create a sales invoice

**Files:**
- Create: `src/Service/DocumentPersister.php`
- Create: `tests/Service/DocumentPersisterTest.php`

**Interfaces:**
- Consumes: `DomainTestCase` from Task 2
- Produces:
  - `DocumentPersister::createSalesInvoice(SalesInvoice $invoice): array` — returns `string[]` warnings
  - `DocumentPersister::snapshotOf(SalesInvoice|PurchaseBill $document): array` — the frozen footprint, for callers to take before mutating

- [ ] **Step 1: Write the failing test**

Create `tests/Service/DocumentPersisterTest.php`:

```php
<?php

declare(strict_types=1);

namespace App\Tests\Service;

use App\Entity\SalesInvoice;
use App\Entity\SalesInvoiceLine;
use App\Service\DocumentPersister;
use App\Tests\DomainTestCase;

class DocumentPersisterTest extends DomainTestCase
{
    private function persister(): DocumentPersister
    {
        return $this->service(DocumentPersister::class);
    }

    private function salesInvoiceFor(float $qty, float $rate = 76.0): SalesInvoice
    {
        $line = (new SalesInvoiceLine())
            ->setItem($this->fabric)
            ->setQty($qty)
            ->setRate($rate);

        return (new SalesInvoice())
            ->setDate(new \DateTimeImmutable('2026-08-09'))
            ->setParty($this->customer)
            ->setPlant($this->plantA)
            ->addLine($line);
    }

    public function testCreatingASalesInvoiceRemovesStockAndAssignsANumber(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);

        $invoice = $this->salesInvoiceFor(100.0);
        $this->persister()->createSalesInvoice($invoice);

        $this->assertSame('SI-0001', $invoice->getNo());
        $this->assertSame(400.0, $this->stockOf($this->fabric, $this->plantA));
        $this->assertEqualsWithDelta(7600.0, $invoice->getTotal(), 0.001);
    }

    public function testSellingMoreThanOnHandStillSavesButWarns(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 10.0);

        $invoice = $this->salesInvoiceFor(100.0);
        $warnings = $this->persister()->createSalesInvoice($invoice);

        $this->assertNotEmpty($warnings, 'A shortfall must warn.');
        $this->assertSame('SI-0001', $invoice->getNo(), 'The sale must still be saved.');
        $this->assertSame(-90.0, $this->stockOf($this->fabric, $this->plantA));
    }
}
```

- [ ] **Step 2: Run it and verify it fails**

Run: `vendor/bin/phpunit tests/Service/DocumentPersisterTest.php`
Expected: FAIL — `App\Service\DocumentPersister` does not exist.

- [ ] **Step 3: Write the implementation**

Create `src/Service/DocumentPersister.php`:

```php
<?php

declare(strict_types=1);

namespace App\Service;

use App\Entity\PurchaseBill;
use App\Entity\SalesInvoice;
use Doctrine\ORM\EntityManagerInterface;

/**
 * The single owner of what happens when a document is written: numbering and
 * stock movement. Both the admin portal and the mobile API go through here, so
 * the two can never drift apart.
 *
 * Sales remove stock, purchases add it. That sign is the only asymmetry, and it
 * is stated once per document type rather than at each call site.
 */
class DocumentPersister
{
    private const SALES_APPLY = -1;
    private const PURCHASE_APPLY = 1;

    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly DocumentStockManager $stockManager,
        private readonly DocumentNumberService $documentNumbers,
    ) {
    }

    /**
     * @return array{plantId: int|null, lines: array<int, array{itemId: int, qty: float}>}
     */
    public function snapshotOf(SalesInvoice|PurchaseBill $document): array
    {
        return $this->stockManager->snapshot($document->getLines(), $document->getPlant());
    }

    /**
     * @return string[] soft stock warnings; never a reason to reject the sale
     */
    public function createSalesInvoice(SalesInvoice $invoice): array
    {
        $this->stockManager->normalise($invoice);
        $invoice->setNo($this->documentNumbers->consume(DocumentNumberService::SALES));

        $this->em->persist($invoice);
        $this->em->flush();

        $warnings = $this->stockManager->salesWarnings($invoice->getLines(), $invoice->getPlant());
        $this->stockManager->applySnapshot($this->snapshotOf($invoice), self::SALES_APPLY);

        return $warnings;
    }
}
```

- [ ] **Step 4: Run the tests and verify they pass**

Run: `vendor/bin/phpunit tests/Service/DocumentPersisterTest.php`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add src/Service/DocumentPersister.php tests/Service/DocumentPersisterTest.php
git commit -m "feat: add DocumentPersister with sales invoice creation"
```

---

### Task 4: Update a sales invoice

This is the subtle case the snapshot mechanism exists for. Stock on hand reflects the document **as stored**, so the stored effect must be undone at the stored plant, with the stored quantities, before the new effect applies.

**Files:**
- Modify: `src/Service/DocumentPersister.php`
- Modify: `tests/Service/DocumentPersisterTest.php`

**Interfaces:**
- Consumes: `snapshotOf()` and `createSalesInvoice()` from Task 3
- Produces: `DocumentPersister::updateSalesInvoice(SalesInvoice $invoice, array $storedSnapshot): array`

- [ ] **Step 1: Write the failing tests**

Append to `tests/Service/DocumentPersisterTest.php`:

```php
    public function testReducingAnInvoiceQuantityReturnsTheDifferenceToStock(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);

        $invoice = $this->salesInvoiceFor(100.0);
        $this->persister()->createSalesInvoice($invoice);
        $this->assertSame(400.0, $this->stockOf($this->fabric, $this->plantA));

        // The caller freezes the stored footprint BEFORE mutating the entity.
        $stored = $this->persister()->snapshotOf($invoice);

        $invoice->getLines()->first()->setQty(60.0);
        $this->persister()->updateSalesInvoice($invoice, $stored);

        // 500 - 60. Not 440 (double-applied) and not 340 (never reversed).
        $this->assertSame(440.0, $this->stockOf($this->fabric, $this->plantA));
    }

    public function testMovingAnInvoiceToAnotherPlantMakesTheOriginalWhole(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);
        $this->seedStock($this->fabric, $this->plantB, 200.0);

        $invoice = $this->salesInvoiceFor(100.0);
        $this->persister()->createSalesInvoice($invoice);

        $stored = $this->persister()->snapshotOf($invoice);
        $invoice->setPlant($this->plantB);
        $this->persister()->updateSalesInvoice($invoice, $stored);

        $this->assertSame(500.0, $this->stockOf($this->fabric, $this->plantA), 'Original plant restored.');
        $this->assertSame(100.0, $this->stockOf($this->fabric, $this->plantB), 'New plant debited.');
    }

    public function testUpdatingDoesNotConsumeASecondDocumentNumber(): void
    {
        $invoice = $this->salesInvoiceFor(10.0);
        $this->persister()->createSalesInvoice($invoice);

        $stored = $this->persister()->snapshotOf($invoice);
        $invoice->getLines()->first()->setQty(20.0);
        $this->persister()->updateSalesInvoice($invoice, $stored);

        $this->assertSame('SI-0001', $invoice->getNo());
    }
```

- [ ] **Step 2: Run and verify they fail**

Run: `vendor/bin/phpunit tests/Service/DocumentPersisterTest.php`
Expected: FAIL — `updateSalesInvoice()` does not exist.

- [ ] **Step 3: Implement**

Add to `src/Service/DocumentPersister.php`:

```php
    /**
     * @param array{plantId: int|null, lines: array<int, array{itemId: int, qty: float}>} $storedSnapshot
     *                                                                                                   taken before the caller mutated the document
     *
     * @return string[]
     */
    public function updateSalesInvoice(SalesInvoice $invoice, array $storedSnapshot): array
    {
        $this->stockManager->normalise($invoice);
        $this->em->flush();

        $warnings = $this->stockManager->salesWarnings(
            $invoice->getLines(),
            $invoice->getPlant(),
            $storedSnapshot,
        );

        // Undo the stored effect at the stored plant, then apply the new one.
        $this->stockManager->applySnapshot($storedSnapshot, -self::SALES_APPLY);
        $this->stockManager->applySnapshot($this->snapshotOf($invoice), self::SALES_APPLY);

        return $warnings;
    }
```

- [ ] **Step 4: Run and verify they pass**

Run: `vendor/bin/phpunit tests/Service/DocumentPersisterTest.php`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add src/Service/DocumentPersister.php tests/Service/DocumentPersisterTest.php
git commit -m "feat: add sales invoice update with stock reversal"
```

---

### Task 5: Delete a sales invoice

**Files:**
- Modify: `src/Service/DocumentPersister.php`
- Modify: `tests/Service/DocumentPersisterTest.php`

**Interfaces:**
- Produces: `DocumentPersister::deleteSalesInvoice(SalesInvoice $invoice): void`

- [ ] **Step 1: Write the failing test**

Append to `tests/Service/DocumentPersisterTest.php`:

```php
    public function testDeletingASalesInvoiceReturnsItsStock(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);

        $invoice = $this->salesInvoiceFor(100.0);
        $this->persister()->createSalesInvoice($invoice);
        $this->assertSame(400.0, $this->stockOf($this->fabric, $this->plantA));

        $this->persister()->deleteSalesInvoice($invoice);

        $this->assertSame(500.0, $this->stockOf($this->fabric, $this->plantA));
    }
```

- [ ] **Step 2: Run and verify it fails**

Run: `vendor/bin/phpunit tests/Service/DocumentPersisterTest.php`
Expected: FAIL — `deleteSalesInvoice()` does not exist.

- [ ] **Step 3: Implement**

Add to `src/Service/DocumentPersister.php`:

```php
    public function deleteSalesInvoice(SalesInvoice $invoice): void
    {
        // Freeze before removal: once the entity is gone its lines are unreadable.
        $snapshot = $this->snapshotOf($invoice);

        $this->em->remove($invoice);
        $this->em->flush();

        $this->stockManager->applySnapshot($snapshot, -self::SALES_APPLY);
    }
```

- [ ] **Step 4: Run and verify it passes**

Run: `vendor/bin/phpunit tests/Service/DocumentPersisterTest.php`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add src/Service/DocumentPersister.php tests/Service/DocumentPersisterTest.php
git commit -m "feat: add sales invoice deletion with stock restoration"
```

---

### Task 6: Purchase bills

Purchase bills mirror sales with inverted signs: creating one **adds** stock.

**Files:**
- Modify: `src/Service/DocumentPersister.php`
- Create: `tests/Service/DocumentPersisterPurchaseTest.php`

**Interfaces:**
- Produces:
  - `DocumentPersister::createPurchaseBill(PurchaseBill $bill): void`
  - `DocumentPersister::updatePurchaseBill(PurchaseBill $bill, array $storedSnapshot): void`
  - `DocumentPersister::deletePurchaseBill(PurchaseBill $bill): void`

Purchase methods return `void`, not warnings: a purchase adds stock, so it can never be short.

- [ ] **Step 1: Write the failing tests**

Create `tests/Service/DocumentPersisterPurchaseTest.php`:

```php
<?php

declare(strict_types=1);

namespace App\Tests\Service;

use App\Entity\PurchaseBill;
use App\Entity\PurchaseBillLine;
use App\Service\DocumentPersister;
use App\Tests\DomainTestCase;

class DocumentPersisterPurchaseTest extends DomainTestCase
{
    private function persister(): DocumentPersister
    {
        return $this->service(DocumentPersister::class);
    }

    private function purchaseBillFor(float $qty, float $rate = 70.0): PurchaseBill
    {
        $line = (new PurchaseBillLine())
            ->setItem($this->fabric)
            ->setQty($qty)
            ->setRate($rate);

        return (new PurchaseBill())
            ->setDate(new \DateTimeImmutable('2026-08-09'))
            ->setParty($this->supplier)
            ->setPlant($this->plantA)
            ->addLine($line);
    }

    public function testCreatingAPurchaseBillAddsStockAndAssignsANumber(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);

        $bill = $this->purchaseBillFor(100.0);
        $this->persister()->createPurchaseBill($bill);

        $this->assertSame('PB-0001', $bill->getNo());
        $this->assertSame(600.0, $this->stockOf($this->fabric, $this->plantA));
    }

    public function testReducingAPurchaseQuantityRemovesTheDifference(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);

        $bill = $this->purchaseBillFor(100.0);
        $this->persister()->createPurchaseBill($bill);
        $this->assertSame(600.0, $this->stockOf($this->fabric, $this->plantA));

        $stored = $this->persister()->snapshotOf($bill);
        $bill->getLines()->first()->setQty(60.0);
        $this->persister()->updatePurchaseBill($bill, $stored);

        $this->assertSame(560.0, $this->stockOf($this->fabric, $this->plantA));
    }

    public function testDeletingAPurchaseBillRemovesItsStock(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);

        $bill = $this->purchaseBillFor(100.0);
        $this->persister()->createPurchaseBill($bill);

        $this->persister()->deletePurchaseBill($bill);

        $this->assertSame(500.0, $this->stockOf($this->fabric, $this->plantA));
    }

    public function testSalesAndPurchaseNumbersAdvanceIndependently(): void
    {
        $firstBill = $this->purchaseBillFor(10.0);
        $this->persister()->createPurchaseBill($firstBill);

        $secondBill = $this->purchaseBillFor(10.0);
        $this->persister()->createPurchaseBill($secondBill);

        $this->assertSame('PB-0001', $firstBill->getNo());
        $this->assertSame('PB-0002', $secondBill->getNo());
    }
}
```

- [ ] **Step 2: Run and verify they fail**

Run: `vendor/bin/phpunit tests/Service/DocumentPersisterPurchaseTest.php`
Expected: FAIL — `createPurchaseBill()` does not exist.

- [ ] **Step 3: Implement**

Add to `src/Service/DocumentPersister.php`:

```php
    public function createPurchaseBill(PurchaseBill $bill): void
    {
        $this->stockManager->normalise($bill);
        $bill->setNo($this->documentNumbers->consume(DocumentNumberService::PURCHASE));

        $this->em->persist($bill);
        $this->em->flush();

        $this->stockManager->applySnapshot($this->snapshotOf($bill), self::PURCHASE_APPLY);
    }

    /**
     * @param array{plantId: int|null, lines: array<int, array{itemId: int, qty: float}>} $storedSnapshot
     */
    public function updatePurchaseBill(PurchaseBill $bill, array $storedSnapshot): void
    {
        $this->stockManager->normalise($bill);
        $this->em->flush();

        $this->stockManager->applySnapshot($storedSnapshot, -self::PURCHASE_APPLY);
        $this->stockManager->applySnapshot($this->snapshotOf($bill), self::PURCHASE_APPLY);
    }

    public function deletePurchaseBill(PurchaseBill $bill): void
    {
        $snapshot = $this->snapshotOf($bill);

        $this->em->remove($bill);
        $this->em->flush();

        $this->stockManager->applySnapshot($snapshot, -self::PURCHASE_APPLY);
    }
```

- [ ] **Step 4: Run the whole suite**

Run: `vendor/bin/phpunit`
Expected: PASS, 11 tests.

- [ ] **Step 5: Commit**

```bash
git add src/Service/DocumentPersister.php tests/Service/DocumentPersisterPurchaseTest.php
git commit -m "feat: add purchase bill write sequence with inverted stock signs"
```

---

### Task 7: Receipt and payment numbering

Receipts and payments move money, not stock, so they need numbering only.

**Files:**
- Modify: `src/Service/DocumentPersister.php`
- Create: `tests/Service/DocumentPersisterVoucherTest.php`

**Interfaces:**
- Produces:
  - `DocumentPersister::createReceipt(Receipt $receipt): void`
  - `DocumentPersister::createPayment(Payment $payment): void`

- [ ] **Step 1: Write the failing test**

Create `tests/Service/DocumentPersisterVoucherTest.php`:

```php
<?php

declare(strict_types=1);

namespace App\Tests\Service;

use App\Entity\Payment;
use App\Entity\Receipt;
use App\Service\DocumentPersister;
use App\Tests\DomainTestCase;

class DocumentPersisterVoucherTest extends DomainTestCase
{
    private function persister(): DocumentPersister
    {
        return $this->service(DocumentPersister::class);
    }

    public function testCreatingAPaymentAssignsAPaymentNumber(): void
    {
        $payment = (new Payment())
            ->setDate(new \DateTimeImmutable('2026-08-09'))
            ->setType('party')
            ->setParty($this->supplier)
            ->setAmount(5000.00)
            ->setMode('Cash');

        $this->persister()->createPayment($payment);

        $this->assertSame('PY-0001', $payment->getNo());
    }

    public function testCreatingAReceiptAssignsAReceiptNumber(): void
    {
        $receipt = (new Receipt())
            ->setDate(new \DateTimeImmutable('2026-08-09'))
            ->setParty($this->customer)
            ->setAmount(2500.00)
            ->setMode('UPI');

        $this->persister()->createReceipt($receipt);

        $this->assertSame('RC-0001', $receipt->getNo());
    }
}
```

The expected prefixes are the `Settings` entity defaults, verified at `src/Entity/Settings.php:35-44`: `SI-`, `PB-`, `RC-`, `PY-`, each followed by a 4-digit zero-padded counter starting at 1. If a test disagrees, fix the test — never the entity.

- [ ] **Step 2: Run and verify it fails**

Run: `vendor/bin/phpunit tests/Service/DocumentPersisterVoucherTest.php`
Expected: FAIL — `createPayment()` does not exist.

- [ ] **Step 3: Implement**

Add to `src/Service/DocumentPersister.php` (and add `use App\Entity\Payment;` / `use App\Entity\Receipt;`):

```php
    public function createReceipt(Receipt $receipt): void
    {
        $receipt->setNo($this->documentNumbers->consume(DocumentNumberService::RECEIPT));

        $this->em->persist($receipt);
        $this->em->flush();
    }

    public function createPayment(Payment $payment): void
    {
        $payment->setNo($this->documentNumbers->consume(DocumentNumberService::PAYMENT));

        $this->em->persist($payment);
        $this->em->flush();
    }
```

- [ ] **Step 4: Run and verify it passes**

Run: `vendor/bin/phpunit`
Expected: PASS, 13 tests.

- [ ] **Step 5: Commit**

```bash
git add src/Service/DocumentPersister.php tests/Service/DocumentPersisterVoucherTest.php
git commit -m "feat: add receipt and payment numbering"
```

---

### Task 8: Wire the sales invoice controller to the service

**Files:**
- Modify: `src/Controller/Admin/SalesInvoiceCrudController.php:126-189`

**Interfaces:**
- Consumes: every method on `DocumentPersister`
- Produces: no new interface — behaviour must be identical

- [ ] **Step 1: Replace the three lifecycle hooks**

In `src/Controller/Admin/SalesInvoiceCrudController.php`, replace the bodies of `persistEntity`, `updateEntity` and `deleteEntity` so the controller keeps only its presentation concern:

```php
    public function persistEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if (!$entityInstance instanceof SalesInvoice) {
            parent::persistEntity($em, $entityInstance);

            return;
        }

        foreach ($this->documentPersister->createSalesInvoice($entityInstance) as $warning) {
            $this->addFlash('warning', $warning);
        }
    }

    public function updateEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if (!$entityInstance instanceof SalesInvoice) {
            parent::updateEntity($em, $entityInstance);

            return;
        }

        foreach ($this->documentPersister->updateSalesInvoice($entityInstance, $this->storedSnapshot) as $warning) {
            $this->addFlash('warning', $warning);
        }
    }

    public function deleteEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if (!$entityInstance instanceof SalesInvoice) {
            parent::deleteEntity($em, $entityInstance);

            return;
        }

        $this->documentPersister->deleteSalesInvoice($entityInstance);
    }
```

- [ ] **Step 2: Inject the service**

Add `DocumentPersister $documentPersister` to the constructor's promoted readonly properties, matching the existing style. Keep `$stockManager` only if `edit()` at line 120 still uses it for `snapshot()` — replace that call with `$this->documentPersister->snapshotOf($invoice)` and drop the now-unused dependency.

- [ ] **Step 3: Verify the container still compiles**

Run: `php bin/console lint:container`
Expected: no errors. Then `php bin/console cache:clear` succeeds.

- [ ] **Step 4: Regression-test the portal by hand**

Start the app and, through the EasyAdmin UI:
1. Create a sales invoice for a known quantity. Confirm the number increments and the item's stock drops by exactly that quantity.
2. Edit it to a smaller quantity. Confirm stock reflects the new quantity, not a double application.
3. Delete it. Confirm stock returns to its starting value.

This is the acceptance gate for the refactor: identical behaviour, new location.

- [ ] **Step 5: Commit**

```bash
git add src/Controller/Admin/SalesInvoiceCrudController.php
git commit -m "refactor: delegate sales invoice writes to DocumentPersister"
```

---

### Task 9: Wire the remaining controllers

**Files:**
- Modify: `src/Controller/Admin/PurchaseBillCrudController.php:117-155`
- Modify: `src/Controller/Admin/ReceiptCrudController.php:63`
- Modify: `src/Controller/Admin/PaymentCrudController.php:86`

**Interfaces:**
- Consumes: `DocumentPersister`
- Produces: nothing new

- [ ] **Step 1: Rewrite the purchase bill hooks**

```php
    public function persistEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if (!$entityInstance instanceof PurchaseBill) {
            parent::persistEntity($em, $entityInstance);

            return;
        }

        $this->documentPersister->createPurchaseBill($entityInstance);
    }

    public function updateEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if (!$entityInstance instanceof PurchaseBill) {
            parent::updateEntity($em, $entityInstance);

            return;
        }

        $this->documentPersister->updatePurchaseBill($entityInstance, $this->storedSnapshot);
    }

    public function deleteEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if (!$entityInstance instanceof PurchaseBill) {
            parent::deleteEntity($em, $entityInstance);

            return;
        }

        $this->documentPersister->deletePurchaseBill($entityInstance);
    }
```

- [ ] **Step 2: Rewrite the receipt and payment hooks**

In `ReceiptCrudController::persistEntity`, replace the `setNo(...consume(RECEIPT))` line plus its `parent::persistEntity()` call with `$this->documentPersister->createReceipt($entityInstance);`, keeping the existing non-`Receipt` guard. Do the same in `PaymentCrudController` with `createPayment()`.

- [ ] **Step 3: Verify**

Run: `php bin/console lint:container && vendor/bin/phpunit`
Expected: no container errors, 13 tests pass.

- [ ] **Step 4: Regression-test the portal by hand**

Create a purchase bill and confirm stock rises; edit it down and confirm the difference is removed; delete it and confirm stock returns. Create one receipt and one payment and confirm both get sequential numbers.

- [ ] **Step 5: Commit**

```bash
git add src/Controller/Admin/PurchaseBillCrudController.php src/Controller/Admin/ReceiptCrudController.php src/Controller/Admin/PaymentCrudController.php
git commit -m "refactor: delegate purchase, receipt and payment writes to DocumentPersister"
```

---

### Task 10: Protect number allocation against concurrency

`consume()` reads a counter, increments it, and flushes. Two simultaneous creates can read the same value and produce duplicate document numbers — and `no` is `unique: true` on both document entities, so the second write fails with an opaque constraint violation.

**Files:**
- Modify: `src/Service/DocumentPersister.php`
- Modify: `tests/Service/DocumentPersisterTest.php`

**Interfaces:**
- Produces: no signature change; creates become transactional

- [ ] **Step 1: Write the failing test**

Append to `tests/Service/DocumentPersisterTest.php`:

```php
    public function testConsecutiveCreatesNeverShareANumber(): void
    {
        $numbers = [];

        for ($i = 0; $i < 5; ++$i) {
            $invoice = $this->salesInvoiceFor(1.0);
            $this->persister()->createSalesInvoice($invoice);
            $numbers[] = $invoice->getNo();
        }

        $this->assertCount(5, array_unique($numbers), 'Every document number must be distinct.');
        $this->assertSame(['SI-0001', 'SI-0002', 'SI-0003', 'SI-0004', 'SI-0005'], $numbers);
    }
```

- [ ] **Step 2: Run it**

Run: `vendor/bin/phpunit tests/Service/DocumentPersisterTest.php`
Expected: PASS already — single-threaded allocation is sequential. This test is a regression guard, not a failing-first test; it locks in the sequence so the transaction change in Step 3 cannot alter it.

- [ ] **Step 3: Wrap creation in a transaction**

In `DocumentPersister`, wrap the numbering-and-persist portion of `createSalesInvoice`, `createPurchaseBill`, `createReceipt` and `createPayment` in `$this->em->wrapInTransaction(...)`. For the sales invoice:

```php
    public function createSalesInvoice(SalesInvoice $invoice): array
    {
        $this->em->wrapInTransaction(function () use ($invoice): void {
            $this->stockManager->normalise($invoice);
            $invoice->setNo($this->documentNumbers->consume(DocumentNumberService::SALES));
            $this->em->persist($invoice);
        });

        $warnings = $this->stockManager->salesWarnings($invoice->getLines(), $invoice->getPlant());
        $this->stockManager->applySnapshot($this->snapshotOf($invoice), self::SALES_APPLY);

        return $warnings;
    }
```

The stock adjustment stays outside the transaction, matching the portal's existing behaviour: the document is committed first and stock follows.

- [ ] **Step 4: Run the whole suite**

Run: `vendor/bin/phpunit`
Expected: PASS, 14 tests. Every earlier assertion about numbers and stock must still hold — that is the point of running the full suite here.

- [ ] **Step 5: Commit**

```bash
git add src/Service/DocumentPersister.php tests/Service/DocumentPersisterTest.php
git commit -m "fix: allocate document numbers inside a transaction"
```

---

## Done when

- `vendor/bin/phpunit` passes with 14 tests covering create/update/delete stock effects for both document types, both sign conventions, plant moves, and number sequencing.
- All four EasyAdmin controllers delegate to `DocumentPersister` and contain no stock arithmetic.
- Manual portal regression confirms invoice and bill create/edit/delete behave exactly as before.

## Next plans

1. **This plan** — backend foundation.
2. **API completion** — nested line items, receipts, staff, staff work, plants, settings read endpoints.
3. **Write API** — POST/PUT on the six writable resources, built on `DocumentPersister`.
4. **Compose screens** — 22 screens, ViewModels, theme alignment, `SyncManager` and `App.kt` fixes.
