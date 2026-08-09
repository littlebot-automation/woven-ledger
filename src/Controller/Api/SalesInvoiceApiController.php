<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\SalesInvoice;
use App\Entity\SalesInvoiceLine;
use App\Repository\ItemRepository;
use App\Repository\PartyRepository;
use App\Repository\PlantRepository;
use App\Repository\SalesInvoiceRepository;
use App\Repository\SettingsRepository;
use App\Service\DocumentPersister;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/api/sales-invoices')]
class SalesInvoiceApiController extends AbstractController
{
    use ApiPayloadTrait;
    use DocumentWriteTrait;

    public function __construct(
        private readonly SalesInvoiceRepository $salesInvoices,
        private readonly PartyRepository $parties,
        private readonly PlantRepository $plants,
        private readonly ItemRepository $items,
        private readonly SettingsRepository $settings,
        private readonly DocumentPersister $persister,
    ) {
    }

    #[Route('', name: 'api_sales_invoices', methods: ['GET'])]
    public function list(): JsonResponse
    {
        return $this->json(array_map(
            $this->payload(...),
            $this->salesInvoices->findBy([], ['date' => 'DESC', 'id' => 'DESC'])
        ));
    }

    #[Route('/{id}', name: 'api_sales_invoice', requirements: ['id' => '\d+'], methods: ['GET'])]
    public function show(int $id): JsonResponse
    {
        $invoice = $this->salesInvoices->find($id);

        return $invoice ? $this->json($this->payload($invoice)) : $this->notFound('Sales invoice');
    }

    /**
     * Raises an invoice and removes its goods from stock.
     *
     * A shortfall is reported in `warnings` and never blocks the sale — the portal
     * is deliberately permissive here and the phone must not be stricter.
     */
    #[Route('', name: 'api_sales_invoice_create', methods: ['POST'])]
    public function create(Request $request): JsonResponse
    {
        $body = $this->decodeBody($request);

        if (null === $body) {
            return $this->malformedBody();
        }

        $read = $this->readDocumentBody($body, 'invoice_date', 'Buyer');

        if ([] !== $read['errors']) {
            return $this->invalid($read['errors']);
        }

        $invoice = new SalesInvoice();
        $this->apply($invoice, $read);

        // Numbering and the stock movement both belong to the persister.
        $warnings = $this->persister->createSalesInvoice($invoice);

        return $this->json(
            $this->payload($invoice) + ['warnings' => $warnings],
            JsonResponse::HTTP_CREATED,
        );
    }

    /**
     * Rewrites an invoice, including its lines.
     *
     * The stored stock footprint is frozen BEFORE the entity is touched: stock on
     * hand reflects the invoice as it was saved, and undoing that exact effect at
     * that exact plant is the only way an edit leaves the count correct.
     */
    #[Route('/{id}', name: 'api_sales_invoice_update', requirements: ['id' => '\d+'], methods: ['PUT'])]
    public function update(int $id, Request $request): JsonResponse
    {
        $invoice = $this->salesInvoices->find($id);

        if (null === $invoice) {
            return $this->notFound('Sales invoice');
        }

        $body = $this->decodeBody($request);

        if (null === $body) {
            return $this->malformedBody();
        }

        $read = $this->readDocumentBody($body, 'invoice_date', 'Buyer');

        if ([] !== $read['errors']) {
            return $this->invalid($read['errors']);
        }

        $storedSnapshot = $this->persister->snapshotOf($invoice);

        $this->apply($invoice, $read);

        $warnings = $this->persister->updateSalesInvoice($invoice, $storedSnapshot);

        return $this->json($this->payload($invoice) + ['warnings' => $warnings]);
    }

    /**
     * @param array{party: mixed, plant: mixed, date: mixed, discount: float, notes: string|null, lines: array<int, array{item: mixed, qty: float, rate: float}>} $read
     */
    private function apply(SalesInvoice $invoice, array $read): void
    {
        $invoice->setParty($read['party']);
        $invoice->setPlant($read['plant']);
        $invoice->setDate($read['date']);
        $invoice->setDiscount($read['discount']);
        $invoice->setNotes($read['notes']);

        // Lines are replaced wholesale: the client sends the document as it should
        // now read, not a patch. orphanRemoval deletes the rows that went away.
        foreach ($invoice->getLines()->toArray() as $existing) {
            $invoice->removeLine($existing);
        }

        foreach ($read['lines'] as $line) {
            $invoice->addLine(
                (new SalesInvoiceLine())
                    ->setItem($line['item'])
                    ->setQty($line['qty'])
                    ->setRate($line['rate'])
            );
        }

        $invoice->recalculateTotals();
    }

    /**
     * @return array<string, mixed>
     */
    private function payload(SalesInvoice $invoice): array
    {
        $stamp = $this->stamp($invoice->getDate());

        return [
            'id' => $invoice->getId(),
            'document_number' => $invoice->getNo(),
            'party_id' => $invoice->getParty()?->getId() ?? 0,
            'plant_id' => $invoice->getPlant()?->getId(),
            'invoice_date' => $invoice->getDate()->format('Y-m-d'),
            'due_date' => null,
            'total_amount' => $this->paise($invoice->getSubtotal()),
            'discount_amount' => $this->paise($invoice->getDiscount()),
            // No GST is modelled anywhere in the portal; kept for wire compatibility.
            'gst_amount' => 0,
            'net_amount' => $this->paise($invoice->getTotal()),
            'status' => 'issued',
            'notes' => $invoice->getNotes(),
            // A document and its lines are one unit and are never fetched apart.
            'lines' => $this->linePayloads($invoice->getLines()),
            'created_at' => $stamp,
            'updated_at' => $stamp,
        ];
    }
}
