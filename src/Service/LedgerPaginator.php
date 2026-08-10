<?php

declare(strict_types=1);

namespace App\Service;

/**
 * Turns a party's full running account into one displayable page of it.
 *
 * This is deliberately a presentation-seam helper rather than anything inside
 * LedgerService: the running balance on every row is cumulative from the oldest
 * entry, so the *only* safe way to page it is to compute the whole ledger first
 * and then cut a window out of the finished list. A LIMIT/OFFSET style query
 * would restart the running total at the top of each page and print balances
 * that are simply wrong.
 *
 * LedgerService keeps returning oldest-first (the API and the phone rely on
 * that); the reversal to newest-first happens here, for the screen only.
 */
class LedgerPaginator
{
    public const PER_PAGE = 20;

    /**
     * @param array<int, array{date: string, type: string, ref: string, amount: float, balance: float, kind: string, id: int|null}> $entries
     *                                                                                                                                       the complete ledger, oldest first, balances already computed
     * @param mixed                                                                                                                  $requestedPage
     *                                                                                                                                       raw ?ledger_page value: may be null, a string, or nonsense
     *
     * @return array{entries: array<int, array<string, mixed>>, current_page: int, page_count: int, total: int, per_page: int}
     */
    public function paginate(array $entries, mixed $requestedPage = null, int $perPage = self::PER_PAGE): array
    {
        $perPage = max(1, $perPage);
        $total = \count($entries);

        // An empty ledger is still "page 1 of 1", so the empty state renders once
        // and the caller never has to special-case a zero page count.
        $pageCount = max(1, (int) ceil($total / $perPage));

        // Anything that is not a number at all (?ledger_page=abc) means page 1;
        // anything out of range is clamped rather than 404/500 or shown blank.
        $page = is_numeric($requestedPage) ? (int) $requestedPage : 1;
        $page = max(1, min($page, $pageCount));

        // Reverse the *finished* list: each row keeps the balance it was given
        // when the total was accumulated oldest-first.
        $newestFirst = array_reverse($entries);

        return [
            'entries' => \array_slice($newestFirst, ($page - 1) * $perPage, $perPage),
            'current_page' => $page,
            'page_count' => $pageCount,
            'total' => $total,
            'per_page' => $perPage,
        ];
    }
}
