<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Payment;
use App\Entity\Staff;
use App\Repository\SettingsRepository;
use App\Repository\StaffRepository;
use App\Service\DocumentNumberService;
use App\Service\NavigationProvider;
use App\Service\WageSettlementService;
use EasyCorp\Bundle\EasyAdminBundle\Config\Action;
use EasyCorp\Bundle\EasyAdminBundle\Router\AdminUrlGenerator;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

/**
 * Wage settlement — spec §4.5.
 */
class StaffSettleController extends AbstractController
{
    public function __construct(
        private readonly WageSettlementService $settlementService,
        private readonly StaffRepository $staffRepository,
        private readonly SettingsRepository $settingsRepository,
        private readonly DocumentNumberService $documentNumbers,
        private readonly NavigationProvider $navigation,
        private readonly AdminUrlGenerator $adminUrlGenerator,
    ) {
    }

    // Deliberately not under /admin/staff-work/... — EasyAdmin's pretty-URL route
    // /admin/staff-work/{entityId} would capture "settle" as an entity id first.
    #[Route('/admin/wages/settle', name: 'admin_staff_settle')]
    public function settle(Request $request): Response
    {
        $outstanding = $this->settlementService->outstanding();

        $backUrl = $this->adminUrlGenerator
            ->setController(StaffWorkCrudController::class)
            ->setAction(Action::INDEX)
            ->generateUrl();

        if ([] === $outstanding) {
            $this->addFlash('info', 'No unpaid wages to settle.');

            return $this->redirect($backUrl);
        }

        if ($request->isMethod('POST')) {
            $staff = $this->staffRepository->find((int) $request->request->get('staff'));

            if (!$staff instanceof Staff) {
                $this->addFlash('danger', 'Select a staff member.');

                return $this->redirectToRoute('admin_staff_settle');
            }

            $dateInput = (string) $request->request->get('date', '');
            $date = '' !== $dateInput
                ? new \DateTimeImmutable($dateInput)
                : new \DateTimeImmutable('today');

            $mode = (string) $request->request->get('mode', 'Cash');

            $result = $this->settlementService->settle($staff, $date, $mode);

            if (null === $result) {
                $this->addFlash('warning', 'No unpaid entries for this staff member.');

                return $this->redirectToRoute('admin_staff_settle');
            }

            $this->addFlash('success', \sprintf(
                'Paid ₹%s to %s — %d entries settled on voucher %s.',
                number_format($result['total'], 2),
                $staff->getName(),
                $result['count'],
                $result['payment']->getNo(),
            ));

            return $this->redirect($backUrl);
        }

        $selectedId = $request->query->get('staff');
        $selected = null !== $selectedId ? $this->staffRepository->find((int) $selectedId) : null;

        [$title] = $this->navigation->getPageMeta('staffwork');

        return $this->render('admin/staff_settle.html.twig', [
            'page_title' => 'Settle Unpaid Wages',
            'page_subtitle' => 'Turn outstanding work entries into one payment voucher',
            'active_nav' => 'staffwork',
            'outstanding' => $outstanding,
            'selected' => $selected,
            'today' => (new \DateTimeImmutable('today'))->format('Y-m-d'),
            'modes' => Payment::MODES,
            'next_payment_no' => $this->documentNumbers->peek(DocumentNumberService::PAYMENT),
            'back_url' => $backUrl,
            'settings' => $this->settingsRepository->getSettings(),
        ]);
    }
}
