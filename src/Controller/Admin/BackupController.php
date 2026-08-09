<?php

declare(strict_types=1);

namespace App\Controller\Admin;

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
use App\Entity\Staff;
use App\Entity\StaffWork;
use App\Repository\SettingsRepository;
use App\Service\NavigationProvider;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpFoundation\ResponseHeaderBag;
use Symfony\Component\Routing\Attribute\Route;

/**
 * JSON backup export and restore — spec §7. Mirrors the shape the original app
 * wrote to localStorage, so a backup taken from the portal is recognisable here.
 */
class BackupController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly SettingsRepository $settingsRepository,
        private readonly NavigationProvider $navigation,
    ) {
    }

    #[Route('/admin/backup', name: 'admin_backup')]
    public function index(): Response
    {
        [$title, $subtitle] = $this->navigation->getPageMeta('backup');

        return $this->render('admin/backup.html.twig', [
            'page_title' => $title,
            'page_subtitle' => $subtitle,
            'active_nav' => 'backup',
            'counts' => $this->counts(),
            'settings' => $this->settingsRepository->getSettings(),
        ]);
    }

    #[Route('/admin/backup/export', name: 'admin_backup_export')]
    public function export(): Response
    {
        $response = new JsonResponse($this->dump(), Response::HTTP_OK, [], false);
        $response->setEncodingOptions(\JSON_PRETTY_PRINT | \JSON_UNESCAPED_UNICODE);

        $filename = \sprintf('woven-ledger-backup-%s.json', (new \DateTimeImmutable())->format('Y-m-d'));
        $response->headers->set(
            'Content-Disposition',
            $response->headers->makeDisposition(ResponseHeaderBag::DISPOSITION_ATTACHMENT, $filename),
        );

        return $response;
    }

    #[Route('/admin/backup/import', name: 'admin_backup_import', methods: ['POST'])]
    public function import(Request $request): Response
    {
        if (!$request->request->getBoolean('confirm')) {
            $this->addFlash('warning', 'Tick the confirmation box — importing replaces all existing data.');

            return $this->redirectToRoute('admin_backup');
        }

        $file = $request->files->get('backup');

        if (null === $file || !$file->isValid()) {
            $this->addFlash('danger', 'Choose a backup file to import.');

            return $this->redirectToRoute('admin_backup');
        }

        try {
            $payload = json_decode((string) file_get_contents($file->getPathname()), true, 512, \JSON_THROW_ON_ERROR);
        } catch (\JsonException $e) {
            $this->addFlash('danger', 'That file is not valid JSON: '.$e->getMessage());

            return $this->redirectToRoute('admin_backup');
        }

        if (!\is_array($payload)) {
            $this->addFlash('danger', 'That backup file is not in the expected format.');

            return $this->redirectToRoute('admin_backup');
        }

        try {
            $this->em->wrapInTransaction(function () use ($payload): void {
                $this->restore($payload);
            });
        } catch (\Throwable $e) {
            $this->addFlash('danger', 'Import failed and nothing was changed: '.$e->getMessage());

            return $this->redirectToRoute('admin_backup');
        }

        $this->addFlash('success', 'Backup restored.');

        return $this->redirectToRoute('admin_backup');
    }

    /** @return array<string, int> */
    private function counts(): array
    {
        $out = [];

        foreach ([
            'Plants' => Plant::class,
            'Parties' => Party::class,
            'Items' => Item::class,
            'Staff' => Staff::class,
            'Sales Invoices' => SalesInvoice::class,
            'Purchase Bills' => PurchaseBill::class,
            'Receipts' => Receipt::class,
            'Payments' => Payment::class,
            'Work Entries' => StaffWork::class,
        ] as $label => $class) {
            $out[$label] = (int) $this->em->createQuery(
                \sprintf('SELECT COUNT(e.id) FROM %s e', $class)
            )->getSingleScalarResult();
        }

        return $out;
    }

    private function dump(): array
    {
        $settings = $this->settingsRepository->getSettings();

        $plants = array_map(static fn (Plant $p): array => [
            'id' => $p->getId(), 'name' => $p->getName(), 'address' => $p->getAddress(),
        ], $this->em->getRepository(Plant::class)->findAll());

        $parties = array_map(static fn (Party $p): array => [
            'id' => $p->getId(),
            'name' => $p->getName(),
            'type' => $p->getType(),
            'phone' => $p->getPhone(),
            'gstin' => $p->getGstin(),
            'address' => $p->getAddress(),
            'openingBalance' => $p->getOpeningBalance(),
            'openingBalanceType' => $p->getOpeningBalanceType(),
            'createdAt' => $p->getCreatedAt()->format('Y-m-d'),
        ], $this->em->getRepository(Party::class)->findAll());

        $items = array_map(static function (Item $i): array {
            $stock = [];
            foreach ($i->getStocks() as $s) {
                $stock[(string) $s->getPlant()?->getId()] = $s->getQty();
            }

            return [
                'id' => $i->getId(),
                'name' => $i->getName(),
                'type' => $i->getType(),
                'unit' => $i->getUnit(),
                'defaultRate' => $i->getDefaultRate(),
                'hsn' => $i->getHsn(),
                'lowStockThreshold' => $i->getLowStockThreshold(),
                'stock' => $stock,
            ];
        }, $this->em->getRepository(Item::class)->findAll());

        $staff = array_map(static fn (Staff $s): array => [
            'id' => $s->getId(),
            'name' => $s->getName(),
            'role' => $s->getRole(),
            'plantId' => $s->getPlant()?->getId(),
            'phone' => $s->getPhone(),
            'wageType' => $s->getWageType(),
            'dailyRate' => $s->getDailyRate(),
            'pieceRate' => $s->getPieceRate(),
        ], $this->em->getRepository(Staff::class)->findAll());

        $lineDump = static fn (iterable $lines): array => array_map(
            static fn ($l): array => [
                'itemId' => $l->getItem()?->getId(),
                'qty' => $l->getQty(),
                'rate' => $l->getRate(),
                'amount' => $l->getAmount(),
            ],
            $lines instanceof \Traversable ? iterator_to_array($lines) : $lines,
        );

        $sales = array_map(static fn (SalesInvoice $s): array => [
            'id' => $s->getId(), 'no' => $s->getNo(), 'date' => $s->getDate()->format('Y-m-d'),
            'partyId' => $s->getParty()?->getId(), 'plantId' => $s->getPlant()?->getId(),
            'items' => $lineDump($s->getLines()),
            'subtotal' => $s->getSubtotal(), 'discount' => $s->getDiscount(),
            'total' => $s->getTotal(), 'notes' => $s->getNotes(),
        ], $this->em->getRepository(SalesInvoice::class)->findAll());

        $purchases = array_map(static fn (PurchaseBill $b): array => [
            'id' => $b->getId(), 'no' => $b->getNo(), 'date' => $b->getDate()->format('Y-m-d'),
            'partyId' => $b->getParty()?->getId(), 'plantId' => $b->getPlant()?->getId(),
            'items' => $lineDump($b->getLines()),
            'subtotal' => $b->getSubtotal(), 'discount' => $b->getDiscount(),
            'total' => $b->getTotal(), 'notes' => $b->getNotes(),
        ], $this->em->getRepository(PurchaseBill::class)->findAll());

        $receipts = array_map(static fn (Receipt $r): array => [
            'id' => $r->getId(), 'no' => $r->getNo(), 'date' => $r->getDate()->format('Y-m-d'),
            'partyId' => $r->getParty()?->getId(), 'amount' => $r->getAmount(),
            'mode' => $r->getMode(), 'notes' => $r->getNotes(),
        ], $this->em->getRepository(Receipt::class)->findAll());

        $payments = array_map(static fn (Payment $p): array => [
            'id' => $p->getId(), 'no' => $p->getNo(), 'date' => $p->getDate()->format('Y-m-d'),
            'type' => $p->getType(), 'partyId' => $p->getParty()?->getId(),
            'staffId' => $p->getStaff()?->getId(), 'amount' => $p->getAmount(),
            'mode' => $p->getMode(), 'notes' => $p->getNotes(),
        ], $this->em->getRepository(Payment::class)->findAll());

        $staffWork = array_map(static fn (StaffWork $w): array => [
            'id' => $w->getId(), 'date' => $w->getDate()->format('Y-m-d'),
            'staffId' => $w->getStaff()?->getId(), 'plantId' => $w->getPlant()?->getId(),
            'workType' => $w->getWorkType(), 'qty' => $w->getQty(), 'rate' => $w->getRate(),
            'amount' => $w->getAmount(), 'paid' => $w->isPaid(),
            'paymentVoucherId' => $w->getPaymentVoucher()?->getId(),
        ], $this->em->getRepository(StaffWork::class)->findAll());

        return [
            'exportedAt' => (new \DateTimeImmutable())->format(\DATE_ATOM),
            'settings' => [
                'companyName' => $settings->getCompanyName(),
                'address' => $settings->getAddress(),
                'phone' => $settings->getPhone(),
                'gstin' => $settings->getGstin(),
                'invoicePrefix' => $settings->getInvoicePrefix(),
                'purchasePrefix' => $settings->getPurchasePrefix(),
                'receiptPrefix' => $settings->getReceiptPrefix(),
                'paymentPrefix' => $settings->getPaymentPrefix(),
                'nextInvoiceNo' => $settings->getNextInvoiceNo(),
                'nextPurchaseNo' => $settings->getNextPurchaseNo(),
                'nextReceiptNo' => $settings->getNextReceiptNo(),
                'nextPaymentNo' => $settings->getNextPaymentNo(),
                'lowStockDefault' => $settings->getLowStockDefault(),
                'defaultWage' => $settings->getDefaultWage(),
                'currentPlantId' => $settings->getCurrentPlant()?->getId(),
            ],
            'plants' => $plants,
            'parties' => $parties,
            'items' => $items,
            'staff' => $staff,
            'salesInvoices' => $sales,
            'purchaseBills' => $purchases,
            'receipts' => $receipts,
            'payments' => $payments,
            'staffWork' => $staffWork,
        ];
    }

    private function restore(array $p): void
    {
        // Children first, so foreign keys never dangle mid-wipe.
        foreach ([
            StaffWork::class, SalesInvoiceLine::class, PurchaseBillLine::class,
            SalesInvoice::class, PurchaseBill::class, Receipt::class, Payment::class,
            ItemStock::class, Item::class, Staff::class, Party::class,
        ] as $class) {
            $this->em->createQuery(\sprintf('DELETE FROM %s e', $class))->execute();
        }

        $settings = $this->settingsRepository->getSettings();
        $settings->setCurrentPlant(null);
        $this->em->flush();

        $this->em->createQuery(\sprintf('DELETE FROM %s e', Plant::class))->execute();

        $plants = [];
        foreach ($p['plants'] ?? [] as $row) {
            $plant = (new Plant())->setName((string) $row['name'])->setAddress($row['address'] ?? null);
            $this->em->persist($plant);
            $plants[(string) $row['id']] = $plant;
        }

        $parties = [];
        foreach ($p['parties'] ?? [] as $row) {
            $party = new Party();
            $party->setName((string) $row['name'])
                ->setType((string) ($row['type'] ?? 'customer'))
                ->setPhone($row['phone'] ?? null)
                ->setGstin($row['gstin'] ?? null)
                ->setAddress($row['address'] ?? null)
                ->setOpeningBalance($row['openingBalance'] ?? 0)
                ->setOpeningBalanceType((string) ($row['openingBalanceType'] ?? 'to_receive'));

            if (!empty($row['createdAt'])) {
                $party->setCreatedAt(new \DateTimeImmutable((string) $row['createdAt']));
            }

            $this->em->persist($party);
            $parties[(string) $row['id']] = $party;
        }

        $items = [];
        foreach ($p['items'] ?? [] as $row) {
            $item = new Item();
            $item->setName((string) $row['name'])
                ->setType((string) ($row['type'] ?? 'raw_material'))
                ->setUnit((string) ($row['unit'] ?? 'pcs'))
                ->setDefaultRate($row['defaultRate'] ?? 0)
                ->setHsn($row['hsn'] ?? null)
                ->setLowStockThreshold($row['lowStockThreshold'] ?? null);

            $this->em->persist($item);
            $items[(string) $row['id']] = $item;

            foreach ($row['stock'] ?? [] as $plantId => $qty) {
                $plant = $plants[(string) $plantId] ?? null;
                if (null === $plant) {
                    continue;
                }

                $stock = (new ItemStock())->setItem($item)->setPlant($plant)->setQty($qty);
                $item->addStock($stock);
                $this->em->persist($stock);
            }
        }

        $staffMembers = [];
        foreach ($p['staff'] ?? [] as $row) {
            $staff = new Staff();
            $staff->setName((string) $row['name'])
                ->setRole($row['role'] ?? null)
                ->setPlant($plants[(string) ($row['plantId'] ?? '')] ?? null)
                ->setPhone($row['phone'] ?? null)
                ->setWageType((string) ($row['wageType'] ?? 'daily'))
                ->setDailyRate($row['dailyRate'] ?? 0)
                ->setPieceRate($row['pieceRate'] ?? 0);

            $this->em->persist($staff);
            $staffMembers[(string) $row['id']] = $staff;
        }

        foreach ($p['salesInvoices'] ?? [] as $row) {
            $invoice = new SalesInvoice();
            $invoice->setNo((string) $row['no'])
                ->setDate(new \DateTimeImmutable((string) $row['date']))
                ->setParty($parties[(string) ($row['partyId'] ?? '')] ?? null)
                ->setPlant($plants[(string) ($row['plantId'] ?? '')] ?? null)
                ->setDiscount($row['discount'] ?? 0)
                ->setNotes($row['notes'] ?? null);

            foreach ($row['items'] ?? [] as $lineRow) {
                $line = (new SalesInvoiceLine())
                    ->setItem($items[(string) ($lineRow['itemId'] ?? '')] ?? null)
                    ->setQty($lineRow['qty'] ?? 0)
                    ->setRate($lineRow['rate'] ?? 0);
                $invoice->addLine($line);
            }

            $invoice->recalculateTotals();
            $this->em->persist($invoice);
        }

        foreach ($p['purchaseBills'] ?? [] as $row) {
            $bill = new PurchaseBill();
            $bill->setNo((string) $row['no'])
                ->setDate(new \DateTimeImmutable((string) $row['date']))
                ->setParty($parties[(string) ($row['partyId'] ?? '')] ?? null)
                ->setPlant($plants[(string) ($row['plantId'] ?? '')] ?? null)
                ->setDiscount($row['discount'] ?? 0)
                ->setNotes($row['notes'] ?? null);

            foreach ($row['items'] ?? [] as $lineRow) {
                $line = (new PurchaseBillLine())
                    ->setItem($items[(string) ($lineRow['itemId'] ?? '')] ?? null)
                    ->setQty($lineRow['qty'] ?? 0)
                    ->setRate($lineRow['rate'] ?? 0);
                $bill->addLine($line);
            }

            $bill->recalculateTotals();
            $this->em->persist($bill);
        }

        foreach ($p['receipts'] ?? [] as $row) {
            $receipt = new Receipt();
            $receipt->setNo((string) $row['no'])
                ->setDate(new \DateTimeImmutable((string) $row['date']))
                ->setParty($parties[(string) ($row['partyId'] ?? '')] ?? null)
                ->setAmount($row['amount'] ?? 0)
                ->setMode((string) ($row['mode'] ?? 'Cash'))
                ->setNotes($row['notes'] ?? null);

            $this->em->persist($receipt);
        }

        $payments = [];
        foreach ($p['payments'] ?? [] as $row) {
            $payment = new Payment();
            $payment->setNo((string) $row['no'])
                ->setDate(new \DateTimeImmutable((string) $row['date']))
                ->setType((string) ($row['type'] ?? 'party'))
                ->setParty($parties[(string) ($row['partyId'] ?? '')] ?? null)
                ->setStaff($staffMembers[(string) ($row['staffId'] ?? '')] ?? null)
                ->setAmount($row['amount'] ?? 0)
                ->setMode((string) ($row['mode'] ?? 'Cash'))
                ->setNotes($row['notes'] ?? null);

            $this->em->persist($payment);
            $payments[(string) $row['id']] = $payment;
        }

        foreach ($p['staffWork'] ?? [] as $row) {
            $work = new StaffWork();
            $work->setDate(new \DateTimeImmutable((string) $row['date']))
                ->setStaff($staffMembers[(string) ($row['staffId'] ?? '')] ?? null)
                ->setPlant($plants[(string) ($row['plantId'] ?? '')] ?? null)
                ->setWorkType((string) ($row['workType'] ?? ''))
                ->setQty($row['qty'] ?? 0)
                ->setRate($row['rate'] ?? 0)
                ->setPaid((bool) ($row['paid'] ?? false))
                ->setPaymentVoucher($payments[(string) ($row['paymentVoucherId'] ?? '')] ?? null);

            $this->em->persist($work);
        }

        $s = $p['settings'] ?? [];
        $settings->setCompanyName((string) ($s['companyName'] ?? 'Your PP Woven Bags Co.'))
            ->setAddress($s['address'] ?? null)
            ->setPhone($s['phone'] ?? null)
            ->setGstin($s['gstin'] ?? null)
            ->setInvoicePrefix((string) ($s['invoicePrefix'] ?? 'SI-'))
            ->setPurchasePrefix((string) ($s['purchasePrefix'] ?? 'PB-'))
            ->setReceiptPrefix((string) ($s['receiptPrefix'] ?? 'RC-'))
            ->setPaymentPrefix((string) ($s['paymentPrefix'] ?? 'PY-'))
            ->setNextInvoiceNo((int) ($s['nextInvoiceNo'] ?? 1))
            ->setNextPurchaseNo((int) ($s['nextPurchaseNo'] ?? 1))
            ->setNextReceiptNo((int) ($s['nextReceiptNo'] ?? 1))
            ->setNextPaymentNo((int) ($s['nextPaymentNo'] ?? 1))
            ->setLowStockDefault($s['lowStockDefault'] ?? 50)
            ->setDefaultWage($s['defaultWage'] ?? 500)
            ->setCurrentPlant($plants[(string) ($s['currentPlantId'] ?? '')] ?? reset($plants) ?: null);

        $this->em->flush();
    }
}
