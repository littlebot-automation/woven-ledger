<?php

declare(strict_types=1);

namespace App\DataFixtures;

use App\Entity\Item;
use App\Entity\ItemStock;
use App\Entity\Party;
use App\Entity\Payment;
use App\Entity\Plant;
use App\Entity\PurchaseBill;
use App\Entity\PurchaseBillLine;
use App\Entity\Receipt;
use App\Entity\SalesInvoice;
use App\Entity\SalesInvoiceLine;
use App\Entity\Settings;
use App\Entity\Staff;
use App\Entity\StaffWork;
use Doctrine\Bundle\FixturesBundle\Fixture;
use Doctrine\Persistence\ObjectManager;

/**
 * Demo data for a PP woven bag plant. Deterministic — no randomness — so the
 * dashboard, reports and ledgers look the same on every load.
 */
class AppFixtures extends Fixture
{
    private const DAY = 86400;

    public function load(ObjectManager $manager): void
    {
        $today = new \DateTimeImmutable('today');
        $ago = static fn (int $days): \DateTimeImmutable => (new \DateTimeImmutable('today'))->modify("-{$days} days");

        // ---------------------------------------------------------------- plants
        $main = (new Plant())->setName('Main Plant')->setAddress('Plot 14, GIDC Industrial Estate, Vatva, Ahmedabad');
        $unit2 = (new Plant())->setName('Unit II')->setAddress('Survey 88, Kathwada Road, Ahmedabad');
        $manager->persist($main);
        $manager->persist($unit2);

        // ---------------------------------------------------------------- settings
        $settings = new Settings();
        $settings->setCompanyName('Sahyog Poly Weaves Pvt. Ltd.')
            ->setAddress("Plot 14, GIDC Industrial Estate\nVatva, Ahmedabad 382445")
            ->setPhone('+91 79 2583 4410')
            ->setGstin('24AABCS1429P1ZR')
            ->setLowStockDefault(50)
            ->setDefaultWage(500)
            ->setCurrentPlant($main);
        $manager->persist($settings);

        // ---------------------------------------------------------------- parties
        $partySpecs = [
            ['Shree Balaji Traders', 'customer', '+91 98250 11234', '24AAACS1234A1Z5', 0, 'to_receive'],
            ['Gujarat Agro Packaging', 'customer', '+91 99049 55120', '24AABCG7788L1ZQ', 18500, 'to_receive'],
            ['Kisan Seeds & Fertilisers', 'customer', '+91 94260 33410', '24AADCK1122M1Z8', 0, 'to_receive'],
            ['Deep Cement Works', 'customer', '+91 90999 71200', '24AAECD4455N1ZT', 42000, 'to_receive'],
            ['Raj Polymers', 'supplier', '+91 98795 22110', '24AAFCR9900P1ZV', 26000, 'to_pay'],
            ['Nirmal Granules LLP', 'supplier', '+91 97250 87451', '24AAGCN3344Q1ZW', 0, 'to_pay'],
            ['Sunrise Packaging Co.', 'both', '+91 93270 66890', '24AAHCS5566R1ZX', 9500, 'to_receive'],
            ['Ambica Threads', 'supplier', '+91 96870 41235', null, 0, 'to_pay'],
        ];

        $parties = [];
        foreach ($partySpecs as $i => [$name, $type, $phone, $gstin, $opening, $openingType]) {
            $party = new Party();
            $party->setName($name)
                ->setType($type)
                ->setPhone($phone)
                ->setGstin($gstin)
                ->setAddress('Ahmedabad, Gujarat')
                ->setOpeningBalance($opening)
                ->setOpeningBalanceType($openingType)
                ->setCreatedAt($ago(180 - $i * 5));

            $manager->persist($party);
            $parties[] = $party;
        }

        // ---------------------------------------------------------------- items
        // [name, type, unit, rate, hsn, threshold, mainQty, unit2Qty]
        $itemSpecs = [
            ['PP Woven Fabric Roll 90 GSM', 'raw_material', 'kg', 92.50, '54072090', null, 1240, 480],
            ['PP Woven Fabric Roll 120 GSM', 'raw_material', 'kg', 98.00, '54072090', null, 860, 310],
            ['PP Woven Fabric Roll 150 GSM', 'raw_material', 'kg', 104.75, '54072090', 80, 42, 25],
            ['PP Granules — Natural', 'raw_material', 'kg', 88.20, '39021000', 500, 3200, 1150],
            ['Master Batch — White', 'raw_material', 'kg', 145.00, '32041790', 40, 28, 12],
            ['Sewing Thread Cone', 'raw_material', 'pcs', 62.00, '54011000', 100, 340, 120],
            ['Cement Bag 50 kg — Laminated', 'finished_good', 'pcs', 14.80, '63053200', 2000, 18400, 7600],
            ['Fertiliser Bag 50 kg', 'finished_good', 'pcs', 12.60, '63053200', 2000, 12250, 4300],
            ['Grain Bag 100 kg', 'finished_good', 'pcs', 21.40, '63053200', 1000, 4870, 1980],
            ['BOPP Laminated Bag — Printed', 'finished_good', 'pcs', 27.90, '63053200', 800, 640, 210],
        ];

        $items = [];
        foreach ($itemSpecs as [$name, $type, $unit, $rate, $hsn, $threshold, $qMain, $qUnit2]) {
            $item = new Item();
            $item->setName($name)
                ->setType($type)
                ->setUnit($unit)
                ->setDefaultRate($rate)
                ->setHsn($hsn)
                ->setLowStockThreshold($threshold);

            $manager->persist($item);

            foreach ([[$main, $qMain], [$unit2, $qUnit2]] as [$plant, $qty]) {
                $stock = (new ItemStock())->setItem($item)->setPlant($plant)->setQty($qty);
                $item->addStock($stock);
                $manager->persist($stock);
            }

            $items[] = $item;
        }

        // ---------------------------------------------------------------- staff
        $staffSpecs = [
            ['Ramesh Patel', 'Loom Operator', $main, 'daily', 620, 0],
            ['Sunita Devi', 'Stitching', $main, 'piece', 0, 1.85],
            ['Imran Shaikh', 'Lamination Helper', $main, 'daily', 540, 0],
            ['Kiran Solanki', 'Printing', $unit2, 'piece', 0, 2.40],
            ['Mahesh Vaghela', 'Loading & Dispatch', $unit2, 'daily', 500, 0],
            ['Priya Chauhan', 'Cutting & Bundling', $main, 'piece', 0, 1.40],
        ];

        $staffMembers = [];
        foreach ($staffSpecs as $i => [$name, $role, $plant, $wageType, $daily, $piece]) {
            $staff = new Staff();
            $staff->setName($name)
                ->setRole($role)
                ->setPlant($plant)
                ->setPhone('+91 9'.(82500000 + $i * 13571))
                ->setWageType($wageType)
                ->setDailyRate($daily)
                ->setPieceRate($piece);

            $manager->persist($staff);
            $staffMembers[] = $staff;
        }

        // ---------------------------------------------------------------- sales
        // [daysAgo, partyIndex, plant, [[itemIndex, qty, rate], ...], discount]
        $salesSpecs = [
            [92, 0, $main, [[6, 5000, 14.80], [7, 2000, 12.60]], 0],
            [88, 1, $main, [[8, 1200, 21.40]], 500],
            [81, 3, $unit2, [[6, 8000, 15.10]], 0],
            [74, 2, $main, [[7, 4500, 12.90], [8, 800, 21.40]], 1200],
            [70, 0, $main, [[9, 600, 27.90]], 0],
            [63, 6, $unit2, [[6, 3000, 14.95]], 0],
            [58, 1, $main, [[7, 6000, 12.75], [6, 1500, 14.80]], 900],
            [51, 3, $unit2, [[8, 2200, 21.90]], 0],
            [45, 2, $main, [[6, 4000, 15.00]], 0],
            [38, 0, $main, [[9, 900, 28.40], [8, 500, 21.40]], 600],
            [31, 1, $unit2, [[7, 3500, 12.80]], 0],
            [25, 6, $main, [[6, 2500, 14.90]], 0],
            [18, 3, $unit2, [[8, 1800, 21.60], [9, 400, 27.90]], 0],
            [11, 2, $main, [[7, 5200, 12.95]], 1500],
            [4, 0, $main, [[6, 6000, 15.20]], 0],
        ];

        $invoiceNo = 1;
        foreach ($salesSpecs as [$daysAgo, $partyIdx, $plant, $lines, $discount]) {
            $invoice = new SalesInvoice();
            $invoice->setNo('SI-'.str_pad((string) $invoiceNo, 4, '0', \STR_PAD_LEFT))
                ->setDate($ago($daysAgo))
                ->setParty($parties[$partyIdx])
                ->setPlant($plant)
                ->setDiscount($discount);

            foreach ($lines as [$itemIdx, $qty, $rate]) {
                $invoice->addLine(
                    (new SalesInvoiceLine())->setItem($items[$itemIdx])->setQty($qty)->setRate($rate)
                );
            }

            $invoice->recalculateTotals();
            $manager->persist($invoice);
            ++$invoiceNo;
        }

        // ---------------------------------------------------------------- purchases
        $purchaseSpecs = [
            [95, 4, $main, [[3, 5000, 86.50], [4, 200, 142.00]], 0],
            [86, 5, $main, [[0, 2000, 90.00]], 0],
            [78, 4, $unit2, [[3, 3000, 87.40]], 1000],
            [69, 7, $main, [[5, 500, 60.50]], 0],
            [60, 5, $main, [[1, 1500, 96.50]], 0],
            [49, 4, $unit2, [[3, 4000, 88.90]], 2000],
            [40, 5, $main, [[2, 800, 103.00]], 0],
            [29, 7, $unit2, [[5, 400, 61.20]], 0],
            [16, 4, $main, [[3, 3500, 89.60], [4, 150, 146.00]], 0],
            [6, 5, $main, [[0, 1800, 92.75]], 0],
        ];

        $billNo = 1;
        foreach ($purchaseSpecs as [$daysAgo, $partyIdx, $plant, $lines, $discount]) {
            $bill = new PurchaseBill();
            $bill->setNo('PB-'.str_pad((string) $billNo, 4, '0', \STR_PAD_LEFT))
                ->setDate($ago($daysAgo))
                ->setParty($parties[$partyIdx])
                ->setPlant($plant)
                ->setDiscount($discount);

            foreach ($lines as [$itemIdx, $qty, $rate]) {
                $bill->addLine(
                    (new PurchaseBillLine())->setItem($items[$itemIdx])->setQty($qty)->setRate($rate)
                );
            }

            $bill->recalculateTotals();
            $manager->persist($bill);
            ++$billNo;
        }

        // ---------------------------------------------------------------- receipts
        $receiptSpecs = [
            [80, 0, 70000, 'Bank Transfer'],
            [66, 3, 110000, 'Cheque'],
            [55, 1, 60000, 'UPI'],
            [42, 2, 45000, 'Bank Transfer'],
            [27, 0, 55000, 'Cash'],
            [14, 6, 30000, 'UPI'],
            [5, 3, 40000, 'Bank Transfer'],
        ];

        $receiptNo = 1;
        foreach ($receiptSpecs as [$daysAgo, $partyIdx, $amount, $mode]) {
            $receipt = new Receipt();
            $receipt->setNo('RC-'.str_pad((string) $receiptNo, 4, '0', \STR_PAD_LEFT))
                ->setDate($ago($daysAgo))
                ->setParty($parties[$partyIdx])
                ->setAmount($amount)
                ->setMode($mode)
                ->setNotes('Against outstanding invoices');

            $manager->persist($receipt);
            ++$receiptNo;
        }

        // ---------------------------------------------------------------- payments
        $paymentNo = 1;
        $paymentSpecs = [
            [83, 4, 300000, 'Bank Transfer'],
            [64, 5, 120000, 'Cheque'],
            [44, 4, 250000, 'Bank Transfer'],
            [21, 7, 40000, 'UPI'],
            [8, 5, 90000, 'Bank Transfer'],
        ];

        foreach ($paymentSpecs as [$daysAgo, $partyIdx, $amount, $mode]) {
            $payment = new Payment();
            $payment->setNo('PY-'.str_pad((string) $paymentNo, 4, '0', \STR_PAD_LEFT))
                ->setDate($ago($daysAgo))
                ->setType('party')
                ->setParty($parties[$partyIdx])
                ->setAmount($amount)
                ->setMode($mode)
                ->setNotes('Against purchase bills');

            $manager->persist($payment);
            ++$paymentNo;
        }

        // ------------------------------------------------------- staff work + wages
        // Older entries are settled through one voucher per staff member; the most
        // recent fortnight stays unpaid so the settlement screen has something to do.
        $workTypes = ['Stitching', 'Loom Operation', 'Lamination', 'Printing', 'Cutting & Bundling', 'Loading'];

        foreach ($staffMembers as $sIdx => $staff) {
            $settled = [];

            for ($n = 0; $n < 5; ++$n) {
                $daysAgo = 60 - ($sIdx * 3) - ($n * 7);
                $qty = 'daily' === $staff->getWageType() ? 1 : (120 + $n * 35 + $sIdx * 10);

                $work = new StaffWork();
                $work->setDate($ago(max(1, $daysAgo)))
                    ->setStaff($staff)
                    ->setPlant($staff->getPlant() ?? $main)
                    ->setWorkType($workTypes[($sIdx + $n) % \count($workTypes)])
                    ->setQty($qty)
                    ->setRate($staff->getEffectiveRate());

                $manager->persist($work);
                $settled[] = $work;
            }

            $total = 0.0;
            foreach ($settled as $work) {
                $total += $work->getAmount();
            }

            $voucher = new Payment();
            $voucher->setNo('PY-'.str_pad((string) $paymentNo, 4, '0', \STR_PAD_LEFT))
                ->setDate($ago(max(1, 20 - $sIdx)))
                ->setType('staff')
                ->setStaff($staff)
                ->setAmount($total)
                ->setMode('Cash')
                ->setNotes(\sprintf('Wage settlement — %d entries', \count($settled)));

            $manager->persist($voucher);
            ++$paymentNo;

            foreach ($settled as $work) {
                $work->setPaid(true);
                $work->setPaymentVoucher($voucher);
            }

            // Unpaid tail.
            for ($n = 0; $n < 3; ++$n) {
                $qty = 'daily' === $staff->getWageType() ? 1 : (140 + $n * 25);

                $work = new StaffWork();
                $work->setDate($ago(max(1, 12 - $n * 4)))
                    ->setStaff($staff)
                    ->setPlant($staff->getPlant() ?? $main)
                    ->setWorkType($workTypes[($sIdx + $n + 2) % \count($workTypes)])
                    ->setQty($qty)
                    ->setRate($staff->getEffectiveRate());

                $manager->persist($work);
            }
        }

        // Counters continue after the seeded documents.
        $settings->setNextInvoiceNo($invoiceNo)
            ->setNextPurchaseNo($billNo)
            ->setNextReceiptNo($receiptNo)
            ->setNextPaymentNo($paymentNo);

        $manager->flush();
    }
}
