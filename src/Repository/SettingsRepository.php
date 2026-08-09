<?php

declare(strict_types=1);

namespace App\Repository;

use App\Entity\Plant;
use App\Entity\Settings;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Settings>
 */
class SettingsRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Settings::class);
    }

    /**
     * The single configuration row, created on first access along with the
     * default "Main Plant" so a fresh install is immediately usable.
     */
    public function getSettings(): Settings
    {
        $settings = $this->createQueryBuilder('s')
            ->orderBy('s.id', 'ASC')
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();

        if ($settings instanceof Settings) {
            return $settings;
        }

        $em = $this->getEntityManager();

        $plant = $em->getRepository(Plant::class)->findOneBy([]);
        if (!$plant instanceof Plant) {
            $plant = (new Plant())->setName('Main Plant');
            $em->persist($plant);
        }

        $settings = new Settings();
        $settings->setCurrentPlant($plant);
        $em->persist($settings);
        $em->flush();

        return $settings;
    }
}
