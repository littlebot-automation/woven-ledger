<?php

declare(strict_types=1);

namespace App\Tests;

use App\Entity\Item;
use App\Entity\Party;
use App\Entity\Plant;
use App\Entity\Settings;
use App\Entity\Staff;
use App\Service\StockService;
use Doctrine\ORM\EntityManagerInterface;
use Doctrine\ORM\Tools\SchemaTool;
use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;

/**
 * Base for the mobile API tests.
 *
 * The same fixture as {@see DomainTestCase}, but driven through the HTTP stack so
 * routing, JSON decoding and the paise boundary are exercised too — the layer the
 * phone actually talks to. The kernel is booted by createClient() rather than
 * bootKernel(), which is why this cannot simply extend DomainTestCase.
 */
abstract class ApiTestCase extends WebTestCase
{
    protected KernelBrowser $client;
    protected EntityManagerInterface $em;
    protected Plant $plantA;
    protected Plant $plantB;
    protected Party $customer;
    protected Party $supplier;
    protected Item $fabric;
    protected Staff $weaver;

    protected function setUp(): void
    {
        $this->client = static::createClient();
        // Without this the kernel is rebuilt per request, and the entity manager the
        // assertions hold would no longer be the one the controller wrote through.
        $this->client->disableReboot();
        $this->em = static::getContainer()->get('doctrine.orm.default_entity_manager');

        // A fresh schema per test: these tests mutate stock and consume document
        // numbers, and leakage would make failures depend on execution order.
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

        $this->weaver = (new Staff())
            ->setName('Ramesh')
            ->setWageType('daily')
            ->setDailyRate(500.00);

        $settings = new Settings();

        foreach ([$this->plantA, $this->plantB, $this->customer, $this->supplier, $this->fabric, $this->weaver, $settings] as $entity) {
            $this->em->persist($entity);
        }

        $this->em->flush();

        // Documents fall back to the configured plant when the client omits one.
        $settings->setCurrentPlant($this->plantA);
        $this->em->flush();
    }

    protected function tearDown(): void
    {
        parent::tearDown();
        $this->em->close();
    }

    /**
     * @param array<string, mixed> $body
     *
     * @return array<string, mixed>
     */
    protected function send(string $method, string $uri, array $body = []): array
    {
        $this->client->request(
            $method,
            $uri,
            server: ['CONTENT_TYPE' => 'application/json'],
            content: json_encode($body, \JSON_THROW_ON_ERROR),
        );

        $content = $this->client->getResponse()->getContent();

        return json_decode((string) $content, true, flags: \JSON_THROW_ON_ERROR);
    }

    protected function statusCode(): int
    {
        return $this->client->getResponse()->getStatusCode();
    }

    /**
     * Stock is read straight from the service rather than from a response, so the
     * assertions describe what the warehouse holds, not what the API claims.
     */
    protected function stockOf(Item $item, Plant $plant): float
    {
        // The request that just ran used its own entity manager state; clearing
        // keeps a stale identity map from answering for the database.
        $this->em->clear();

        return static::getContainer()->get(StockService::class)->getQty(
            $this->em->find(Item::class, $item->getId()),
            $this->em->find(Plant::class, $plant->getId()),
        );
    }

    protected function seedStock(Item $item, Plant $plant, float $qty): void
    {
        static::getContainer()->get(StockService::class)->setQty($item, $plant, $qty);
    }
}
