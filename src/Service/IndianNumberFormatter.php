<?php

declare(strict_types=1);

namespace App\Service;

/**
 * Numbers as the original app rendered them via toLocaleString('en-IN').
 */
class IndianNumberFormatter
{
    public function inr(float|int|string|null $value, bool $symbol = true): string
    {
        $value = (float) ($value ?? 0);

        // Round to paise first so a carry (99.999) lands in the whole part
        // rather than being dropped by a separate floor().
        $rounded = number_format(abs($value), 2, '.', '');
        [$whole, $decimals] = explode('.', $rounded);

        return $this->sign($value, $rounded).($symbol ? '₹' : '').$this->group($whole).'.'.$decimals;
    }

    /** Quantities lose their trailing zeros — "12.5" reads better than "12.500". */
    public function qty(float|int|string|null $value): string
    {
        $value = (float) ($value ?? 0);

        $rounded = number_format(abs($value), 3, '.', '');
        [$whole, $decimals] = explode('.', $rounded);
        $decimals = rtrim($decimals, '0');

        return $this->sign($value, $rounded)
            .$this->group($whole)
            .('' === $decimals ? '' : '.'.$decimals);
    }

    /**
     * Indian digit grouping: the last three digits, then pairs — 12,34,567.
     * PHP's number_format cannot do this.
     */
    private function group(string $whole): string
    {
        if (\strlen($whole) <= 3) {
            return $whole;
        }

        $rest = preg_replace('/\B(?=(\d{2})+(?!\d))/', ',', substr($whole, 0, -3));

        return $rest.','.substr($whole, -3);
    }

    /** An amount that rounds away to zero is not "minus zero". */
    private function sign(float $value, string $rounded): string
    {
        return $value < 0 && 0.0 !== (float) $rounded ? '-' : '';
    }
}
