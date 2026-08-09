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
