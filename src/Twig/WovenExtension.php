<?php

declare(strict_types=1);

namespace App\Twig;

use App\Service\IndianNumberFormatter;
use Twig\Extension\AbstractExtension;
use Twig\TwigFilter;

class WovenExtension extends AbstractExtension
{
    public function __construct(
        private readonly IndianNumberFormatter $numbers,
    ) {
    }

    public function getFilters(): array
    {
        return [
            new TwigFilter('inr', $this->inr(...), ['is_safe' => ['html']]),
            new TwigFilter('qty', $this->qty(...)),
        ];
    }

    public function inr(float|int|string|null $value, bool $symbol = true): string
    {
        return $this->numbers->inr($value, $symbol);
    }

    public function qty(float|int|string|null $value): string
    {
        return $this->numbers->qty($value);
    }
}
