<?php

declare(strict_types=1);

namespace App\Tests;

use App\Entity\Item;
use App\Entity\Party;
use App\Entity\Plant;
use App\Entity\Settings;
use App\Service\StockService;
use Doctrine\ORM\EntityManagerInterface;
use Doctrine\ORM\Tools\SchemaTool;
use Symfony\Bundle\FrameworkBundle\Test\KernelTestCase;

abstract class DomainTestCase extends KernelTestCase
{
    protected EntityManagerInterface $em;
    protected Plant $plantA;
    protected Plant $plantB;
    protected Party $customer;
    protected Party $supplier;
    protected Item $fabric;

    protected function setUp(): void
    {
        self::bootKernel();

        $this->em = static::getContainer()->get('doctrine.orm.default_entity_manager');

        // A fresh schema per test: these tests mutate stock, and leakage between
        // them would make failures depend on execution order.
        $tool = new SchemaTool($this->em);
        $metadata = $this->em->getMetadataFactory()->getAllMetadata();
        $tool->dropSchema($metadata);
        $tool->createSchema($metadata);

        $this->plantA = (new Plant())->setName('Plant A');
        $this->plantB = (new Plant())->setName('Plant B');

        $this->customer = (new Party())->setName('Acme Sacks')->setType('customer');
        $this->supplier = (new Party())->setName('Ambica Threads')->setType('supplier');

        $this->fabric = (new Item())
            ->setName('PP Fabric Roll')
            ->setType('raw_material')
            ->setUnit('kg')
            ->setDefaultRate(76.00);

        // Settings is a single row the numbering service reads; without it,
        // consume() has nothing to increment.
        $settings = new Settings();

        foreach ([$this->plantA, $this->plantB, $this->customer, $this->supplier, $this->fabric, $settings] as $entity) {
            $this->em->persist($entity);
        }

        $this->em->flush();
    }

    protected function tearDown(): void
    {
        parent::tearDown();
        $this->em->close();
    }

    protected function service(string $class): object
    {
        return static::getContainer()->get($class);
    }

    protected function stockOf(Item $item, Plant $plant): float
    {
        return $this->service(StockService::class)->getQty($item, $plant);
    }

    protected function seedStock(Item $item, Plant $plant, float $qty): void
    {
        $this->service(StockService::class)->setQty($item, $plant, $qty);
    }
}
