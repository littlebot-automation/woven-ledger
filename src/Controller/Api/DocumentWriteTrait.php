<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\Item;
use App\Entity\Party;
use App\Entity\Plant;

/**
 * Reading a sales invoice or purchase bill off the wire.
 *
 * The two documents are the same shape with the party in a different role and the
 * stock sign flipped, so the request is validated once here and each controller
 * only builds its own entity and line objects from the result. What this trait
 * deliberately does NOT do is touch stock or numbering — those belong to
 * {@see \App\Service\DocumentPersister} and nowhere else.
 *
 * Using controllers must expose `$parties`, `$plants`, `$items` and `$settings`.
 */
trait DocumentWriteTrait
{
    /**
     * @param array<string, mixed> $body
     *
     * @return array{
     *     errors: array<string, string>,
     *     party: Party|null,
     *     plant: Plant|null,
     *     date: \DateTimeImmutable|null,
     *     discount: float,
     *     notes: string|null,
     *     lines: list<array{item: Item, qty: float, rate: float}>
     * }
     */
    private function readDocumentBody(array $body, string $dateField, string $partyLabel): array
    {
        $errors = [];

        $date = $this->parseDate($body[$dateField] ?? null, $dateField, $errors);
        $discount = $this->parseMoney($body['discount_amount'] ?? null, 'discount_amount', $errors) ?? 0.0;

        $party = null;
        $partyId = $body['party_id'] ?? null;
        if (null === $partyId || '' === $partyId) {
            $errors['party_id'] = $partyLabel.' is required';
        } else {
            $party = $this->parties->find((int) $partyId);
            if (null === $party) {
                $errors['party_id'] = $partyLabel.' not found';
            }
        }

        // A document must sit at a plant — that is where its stock moves. The app
        // may leave it out, in which case the portal's current plant applies.
        $plant = null;
        $plantId = $body['plant_id'] ?? null;
        if (null === $plantId || '' === $plantId) {
            $plant = $this->settings->getSettings()->getCurrentPlant();
            if (null === $plant) {
                $errors['plant_id'] = 'Plant is required';
            }
        } else {
            $plant = $this->plants->find((int) $plantId);
            if (null === $plant) {
                $errors['plant_id'] = 'Plant not found';
            }
        }

        $lines = [];
        $errors += $this->readLines($body['lines'] ?? null, $lines);

        return [
            'errors' => $errors,
            'party' => $party,
            'plant' => $plant,
            'date' => $date,
            'discount' => $discount,
            'notes' => $this->optionalText($body['notes'] ?? null),
            'lines' => $lines,
        ];
    }

    /**
     * @param list<array{item: Item, qty: float, rate: float}>|null $lines out-param: the resolved lines
     *
     * @return array<string, string> a `lines` error, or none
     */
    private function readLines(mixed $raw, ?array &$lines): array
    {
        $lines = [];

        if (!\is_array($raw) || [] === $raw) {
            return ['lines' => 'At least one line is required'];
        }

        foreach (array_values($raw) as $index => $line) {
            $number = $index + 1;

            if (!\is_array($line)) {
                return ['lines' => \sprintf('Line %d is not an object', $number)];
            }

            $item = $this->items->find((int) ($line['item_id'] ?? 0));
            if (!$item instanceof Item) {
                return ['lines' => \sprintf('Line %d: item not found', $number)];
            }

            $qty = $line['qty'] ?? null;
            if (!is_numeric($qty) || (float) $qty <= 0) {
                return ['lines' => \sprintf('Line %d: qty must be greater than zero', $number)];
            }

            // Rate arrives as integer paise; omitting it takes the item's own rate,
            // which is what the portal's form prefills.
            $rawRate = $line['rate'] ?? null;
            if (null === $rawRate || '' === $rawRate) {
                $rate = $item->getDefaultRate();
            } elseif (!is_numeric($rawRate) || (int) $rawRate < 0) {
                return ['lines' => \sprintf('Line %d: rate must be a whole number of paise', $number)];
            } else {
                $rate = $this->rupees((int) $rawRate);
            }

            $lines[] = ['item' => $item, 'qty' => (float) $qty, 'rate' => $rate];
        }

        return [];
    }

    /**
     * @return array<int, array<string, mixed>>
     */
    private function linePayloads(iterable $lines): array
    {
        $payload = [];

        foreach ($lines as $line) {
            $payload[] = [
                'id' => $line->getId(),
                'item_id' => $line->getItem()?->getId(),
                // Qty is a count of goods, not money, so it stays a decimal.
                'qty' => $line->getQty(),
                'rate' => $this->paise($line->getRate()),
                'amount' => $this->paise($line->getAmount()),
            ];
        }

        return $payload;
    }
}
