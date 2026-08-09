<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\Plant;
use App\Repository\PlantRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\Routing\Attribute\Route;

/**
 * Plants, read-only.
 *
 * Every invoice and bill is booked at a plant, and that choice decides which
 * plant's stock moves, so a create form cannot be honest without this list. The
 * app previously invented a placeholder "Plant 1" row to satisfy its foreign
 * keys; this is what replaces it. Plants themselves are still maintained on the
 * portal.
 */
#[Route('/api/plants')]
class PlantApiController extends AbstractController
{
    use ApiPayloadTrait;

    public function __construct(
        private readonly PlantRepository $plants,
    ) {
    }

    #[Route('', name: 'api_plants', methods: ['GET'])]
    public function list(): JsonResponse
    {
        return $this->json(array_map(
            $this->payload(...),
            $this->plants->findBy([], ['name' => 'ASC'])
        ));
    }

    #[Route('/{id}', name: 'api_plant', requirements: ['id' => '\d+'], methods: ['GET'])]
    public function show(int $id): JsonResponse
    {
        $plant = $this->plants->find($id);

        return $plant ? $this->json($this->payload($plant)) : $this->notFound('Plant');
    }

    /**
     * @return array<string, mixed>
     */
    private function payload(Plant $plant): array
    {
        return [
            'id' => $plant->getId(),
            'name' => $plant->getName(),
            'address' => $plant->getAddress(),
        ];
    }
}
