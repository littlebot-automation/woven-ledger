<?php

declare(strict_types=1);

namespace App\Twig;

use App\Service\NavigationProvider;
use Twig\Extension\AbstractExtension;
use Twig\TwigFilter;
use Twig\TwigFunction;

class WovenExtension extends AbstractExtension
{
    public function __construct(
        private readonly NavigationProvider $navigation,
    ) {
    }

    public function getFilters(): array
    {
        return [
            new TwigFilter('inr', $this->inr(...), ['is_safe' => ['html']]),
            new TwigFilter('qty', $this->qty(...)),
        ];
    }

    public function getFunctions(): array
    {
        return [
            new TwigFunction('wl_nav', $this->navigation->getGroups(...)),
        ];
    }

    /**
     * Indian digit grouping: the last three digits, then pairs — 12,34,567.89.
     * PHP's number_format cannot do this, and en-IN is exactly what the original
     * app rendered via toLocaleString('en-IN').
     */
    public function inr(float|int|string|null $value, bool $symbol = true): string
    {
        $value = (float) ($value ?? 0);
        $negative = $value < 0;
        $value = abs($value);

        $whole = (string) (int) floor($value);
        $decimals = number_format($value - floor($value), 2, '.', '');
        $decimals = substr($decimals, 2);

        if (\strlen($whole) > 3) {
            $last3 = substr($whole, -3);
            $rest = substr($whole, 0, -3);
            $rest = preg_replace('/\B(?=(\d{2})+(?!\d))/', ',', $rest);
            $whole = $rest.','.$last3;
        }

        return ($negative ? '-' : '').($symbol ? '₹' : '').$whole.'.'.$decimals;
    }

    /** Quantities lose their trailing zeros — "12.5" reads better than "12.500". */
    public function qty(float|int|string|null $value): string
    {
        $formatted = number_format((float) ($value ?? 0), 3, '.', ',');

        return str_contains($formatted, '.')
            ? rtrim(rtrim($formatted, '0'), '.')
            : $formatted;
    }
}
