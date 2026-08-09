<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Item;
use App\Entity\Plant;
use App\Repository\PlantRepository;
use App\Repository\SettingsRepository;
use App\Service\NavigationProvider;
use App\Service\StockService;
use EasyCorp\Bundle\EasyAdminBundle\Attribute\AdminRoute;
use EasyCorp\Bundle\EasyAdminBundle\Config\Action;
use EasyCorp\Bundle\EasyAdminBundle\Router\AdminUrlGenerator;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * Manual stock adjustment — spec §4.2. Used for opening stock, physical-count
 * corrections and wastage; sales and purchases move stock on their own.
 */
class ItemStockController extends AbstractController
{
    public function __construct(
        private readonly StockService $stockService,
        private readonly PlantRepository $plantRepository,
        private readonly SettingsRepository $settingsRepository,
        private readonly NavigationProvider $navigation,
        private readonly AdminUrlGenerator $adminUrlGenerator,
    ) {
    }

    #[AdminRoute(path: '/item/{id}/adjust', name: 'item_adjust', options: ['requirements' => ['id' => '\d+']])]
    public function adjust(Item $item, Request $request): Response
    {
        $plants = $this->plantRepository->findAllOrdered();
        $backUrl = $this->adminUrlGenerator
            ->setController(ItemCrudController::class)
            ->setAction(Action::INDEX)
            ->generateUrl();

        if ($request->isMethod('POST')) {
            $plant = $this->plantRepository->find((int) $request->request->get('plant'));

            if (!$plant instanceof Plant) {
                $this->addFlash('danger', 'Select a plant.');

                return $this->redirectToRoute('admin_item_adjust', ['id' => $item->getId()]);
            }

            $absolute = trim((string) $request->request->get('absolute', ''));
            $delta = trim((string) $request->request->get('delta', ''));

            // An absolute value wins when both boxes are filled.
            if ('' !== $absolute) {
                $this->stockService->setQty($item, $plant, (float) $absolute);
                $this->addFlash('success', \sprintf(
                    'Stock for "%s" at %s set to %s %s.',
                    $item->getName(), $plant->getName(), $absolute, $item->getUnit(),
                ));
            } elseif ('' !== $delta) {
                $this->stockService->adjust($item, $plant, (float) $delta);
                $this->addFlash('success', \sprintf(
                    'Stock for "%s" at %s adjusted by %s %s.',
                    $item->getName(), $plant->getName(), $delta, $item->getUnit(),
                ));
            } else {
                $this->addFlash('warning', 'Enter either an absolute quantity or an adjustment.');

                return $this->redirectToRoute('admin_item_adjust', ['id' => $item->getId()]);
            }

            return $this->redirect($backUrl);
        }

        $current = [];
        foreach ($plants as $plant) {
            $current[(int) $plant->getId()] = $this->stockService->getQty($item, $plant);
        }

        [$title] = $this->navigation->getPageMeta('items');

        return $this->render('admin/item_adjust.html.twig', [
            'page_title' => 'Adjust Stock — '.$item->getName(),
            'item' => $item,
            'plants' => $plants,
            'current' => $current,
            'threshold' => $this->stockService->thresholdFor($item),
            'back_url' => $backUrl,
            'settings' => $this->settingsRepository->getSettings(),
        ]);
    }
}
