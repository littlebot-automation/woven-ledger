<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Party;
use App\Repository\PartyRepository;
use App\Repository\SettingsRepository;
use App\Service\LedgerService;
use App\Service\NavigationProvider;
use EasyCorp\Bundle\EasyAdminBundle\Attribute\AdminRoute;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

class LedgerController extends AbstractController
{
    public function __construct(
        private readonly LedgerService $ledgerService,
        private readonly PartyRepository $partyRepository,
        private readonly SettingsRepository $settingsRepository,
        private readonly NavigationProvider $navigation,
    ) {
    }

    /**
     * #[AdminRoute] rather than #[Route] so the page gets an AdminContext and can
     * render inside the EasyAdmin layout. The path and name are appended to the
     * dashboard's, so this stays /admin/ledger and 'admin_ledger'.
     */
    #[AdminRoute(path: '/ledger', name: 'ledger')]
    public function index(Request $request): Response
    {
        $parties = $this->partyRepository->findBy([], ['name' => 'ASC']);

        $selected = null;
        $requestedId = $request->query->get('party');

        if (null !== $requestedId && '' !== $requestedId) {
            $selected = $this->partyRepository->find((int) $requestedId);
        }

        $selected ??= $parties[0] ?? null;

        [$title] = $this->navigation->getPageMeta('ledger');

        return $this->render('admin/ledger.html.twig', [
            'page_title' => $title,
            'parties' => $parties,
            'party' => $selected,
            'entries' => $selected instanceof Party ? $this->ledgerService->getEntries($selected) : [],
            'balance' => $selected instanceof Party ? $this->ledgerService->getBalance($selected) : 0.0,
            'settings' => $this->settingsRepository->getSettings(),
        ]);
    }

    #[Route('/admin/ledger/{id}/print', name: 'admin_ledger_print', requirements: ['id' => '\d+'])]
    public function print(Party $party): Response
    {
        return $this->render('admin/ledger_print.html.twig', [
            'party' => $party,
            'entries' => $this->ledgerService->getEntries($party),
            'balance' => $this->ledgerService->getBalance($party),
            'settings' => $this->settingsRepository->getSettings(),
        ]);
    }
}
