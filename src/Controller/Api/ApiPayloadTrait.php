<?php

declare(strict_types=1);

namespace App\Controller\Api;

use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;

/**
 * Shared wire conventions for the mobile API controllers.
 *
 * Money crosses the wire as integer paise because the Room entities on the
 * Android side store it that way; the Doctrine entities keep decimal rupees.
 * Conversion happens here, at the boundary, in both directions — never inside a
 * controller body, where one missing ×100 would silently misprice a document.
 */
trait ApiPayloadTrait
{
    private function paise(float $rupees): int
    {
        return (int) round($rupees * 100);
    }

    /** The inverse of {@see paise()}: what arrives on the wire, as Doctrine stores it. */
    private function rupees(int|float|string $paise): float
    {
        return (int) $paise / 100;
    }

    private function stamp(\DateTimeImmutable $date): string
    {
        return $date->format('Y-m-d\TH:i:s');
    }

    private function notFound(string $label): JsonResponse
    {
        return $this->json(['error' => $label.' not found'], JsonResponse::HTTP_NOT_FOUND);
    }

    /**
     * The request body as an array, or null when it is not a JSON object.
     *
     * @return array<string, mixed>|null
     */
    private function decodeBody(Request $request): ?array
    {
        $body = json_decode($request->getContent(), true);

        return \is_array($body) ? $body : null;
    }

    /**
     * A 422 carrying the per-field error map the app renders against its inputs.
     *
     * @param array<string, string> $errors
     */
    private function invalid(array $errors): JsonResponse
    {
        return $this->json(['errors' => $errors], JsonResponse::HTTP_UNPROCESSABLE_ENTITY);
    }

    private function malformedBody(): JsonResponse
    {
        return $this->invalid(['body' => 'Expected a JSON object']);
    }

    /**
     * A 'Y-m-d' date. Absent means "today", which is what every portal form
     * defaults to; a malformed one is an error rather than a silent today.
     *
     * @param array<string, string> $errors
     */
    private function parseDate(mixed $raw, string $field, array &$errors): ?\DateTimeImmutable
    {
        if (null === $raw || '' === $raw) {
            return new \DateTimeImmutable('today');
        }

        // The leading '!' zeroes the time, so two documents dated the same day
        // never differ by the hour they happened to be posted at.
        $parsed = \DateTimeImmutable::createFromFormat('!Y-m-d', (string) $raw);

        if (false === $parsed) {
            $errors[$field] = 'Date must be formatted YYYY-MM-DD';

            return null;
        }

        return $parsed;
    }

    /**
     * An integer-paise field, returned as the decimal rupees Doctrine stores.
     *
     * @param array<string, string> $errors
     */
    private function parseMoney(
        mixed $raw,
        string $field,
        array &$errors,
        bool $required = false,
        bool $positive = false,
    ): ?float {
        if (null === $raw || '' === $raw) {
            if ($required) {
                $errors[$field] = 'An amount is required';

                return null;
            }

            return 0.0;
        }

        if (!is_numeric($raw)) {
            $errors[$field] = 'Amount must be a whole number of paise';

            return null;
        }

        $rupees = $this->rupees((int) $raw);

        if ($positive && $rupees <= 0) {
            $errors[$field] = 'Enter an amount greater than zero';

            return null;
        }

        if (!$positive && $rupees < 0) {
            $errors[$field] = 'Amount cannot be negative';

            return null;
        }

        return $rupees;
    }

    /**
     * Check a value against an entity's own allow-list — the `const` arrays are
     * label => value, so the values are what the wire may carry.
     *
     * @param array<string, string> $allowed entity const, e.g. Party::TYPES
     * @param array<string, string> $errors
     */
    private function parseChoice(
        mixed $raw,
        string $field,
        array $allowed,
        array &$errors,
        ?string $default = null,
    ): ?string {
        if (null === $raw || '' === $raw) {
            if (null !== $default) {
                return $default;
            }

            $errors[$field] = 'A value is required';

            return null;
        }

        $value = (string) $raw;

        if (!\in_array($value, array_values($allowed), true)) {
            $errors[$field] = \sprintf('Must be one of: %s', implode(', ', array_values($allowed)));

            return null;
        }

        return $value;
    }

    /** Trimmed text, or null when the client sent nothing at all. */
    private function optionalText(mixed $raw): ?string
    {
        if (null === $raw) {
            return null;
        }

        $text = trim((string) $raw);

        return '' === $text ? null : $text;
    }
}
