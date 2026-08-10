<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Payment;
use App\Entity\Staff;
use App\Entity\StaffWork;
use App\Form\EventListener\DefaultRateFromStaffListener;
use App\Repository\StaffRepository;
use App\Service\DocumentNumberService;
use App\Service\IndianNumberFormatter;
use App\Service\WageSettlementService;
use Doctrine\ORM\EntityManagerInterface;
use EasyCorp\Bundle\EasyAdminBundle\Attribute\AdminRoute;
use EasyCorp\Bundle\EasyAdminBundle\Config\Action;
use EasyCorp\Bundle\EasyAdminBundle\Config\Actions;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
use EasyCorp\Bundle\EasyAdminBundle\Config\Filters;
use EasyCorp\Bundle\EasyAdminBundle\Config\KeyValueStore;
use EasyCorp\Bundle\EasyAdminBundle\Context\AdminContext;
use EasyCorp\Bundle\EasyAdminBundle\Controller\AbstractCrudController;
use EasyCorp\Bundle\EasyAdminBundle\Dto\EntityDto;
use EasyCorp\Bundle\EasyAdminBundle\Field\AssociationField;
use EasyCorp\Bundle\EasyAdminBundle\Field\BooleanField;
use EasyCorp\Bundle\EasyAdminBundle\Field\DateField;
use EasyCorp\Bundle\EasyAdminBundle\Field\MoneyField;
use EasyCorp\Bundle\EasyAdminBundle\Field\NumberField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextField;
use EasyCorp\Bundle\EasyAdminBundle\Filter\BooleanFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\DateTimeFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\EntityFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\NumericFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\TextFilter;
use EasyCorp\Bundle\EasyAdminBundle\Router\AdminUrlGenerator;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;

class StaffWorkCrudController extends AbstractCrudController
{
    public function __construct(
        private readonly WageSettlementService $settlementService,
        private readonly StaffRepository $staffRepository,
        private readonly DocumentNumberService $documentNumbers,
        private readonly AdminUrlGenerator $adminUrlGenerator,
        private readonly IndianNumberFormatter $numbers,
        private readonly DefaultRateFromStaffListener $defaultRate,
    ) {
    }

    public static function getEntityFqcn(): string
    {
        return StaffWork::class;
    }

    public function configureCrud(Crud $crud): Crud
    {
        return $crud
            ->setEntityLabelInSingular('Work Entry')
            ->setEntityLabelInPlural('Daily Staff Work')
            ->setPageTitle(Crud::PAGE_INDEX, 'Daily Staff Work')
            ->setHelp(Crud::PAGE_INDEX, 'Log work and settle staff wages')
            ->setDefaultSort(['date' => 'DESC', 'id' => 'DESC'])
            ->setSearchFields(['workType'])
            ->setDateFormat('dd MMM yyyy')
            // Clicking a row opens the read-only view rather than the form.
            // Enabling the detail action is not enough on its own: EasyAdmin's
            // default row action is the chain [EDIT, DETAIL], and EDIT is enabled
            // here, so it would keep winning. Naming DETAIL moves the link.
            ->setDefaultRowAction(Action::DETAIL);
    }

    public function configureActions(Actions $actions): Actions
    {
        $settle = Action::new('settleWages', 'Settle Unpaid Wages', 'fa fa-hand-holding-dollar')
            ->linkToCrudAction('settleWages')
            ->createAsGlobalAction();

        return $actions
            ->add(Crud::PAGE_INDEX, $settle)
            // DETAIL is not a default index action, so it is added for the row link.
            ->add(Crud::PAGE_INDEX, Action::DETAIL);
    }

    /**
     * Wage settlement — spec §4.5.
     *
     * The path is '/settle-wages' rather than '/settle' only so it can never be
     * confused with an entity id by the detail route /admin/staff-work/{entityId};
     * EasyAdmin registers this route first, but the explicit name keeps it obvious.
     */
    #[AdminRoute(path: '/settle-wages', name: 'settle_wages', options: ['methods' => ['GET', 'POST']])]
    public function settleWages(Request $request): Response
    {
        $outstanding = $this->settlementService->outstanding();

        $backUrl = $this->adminUrlGenerator
            ->setController(self::class)
            ->setAction(Action::INDEX)
            ->unset('entityId')
            ->generateUrl();

        $selfUrl = $this->adminUrlGenerator
            ->setController(self::class)
            ->setAction('settleWages')
            ->unset('entityId')
            ->generateUrl();

        if ([] === $outstanding) {
            $this->addFlash('info', 'No unpaid wages to settle.');

            return $this->redirect($backUrl);
        }

        if ($request->isMethod('POST')) {
            $staff = $this->staffRepository->find((int) $request->request->get('staff'));

            if (!$staff instanceof Staff) {
                $this->addFlash('danger', 'Select a staff member.');

                return $this->redirect($selfUrl);
            }

            $dateInput = (string) $request->request->get('date', '');
            $date = '' !== $dateInput
                ? new \DateTimeImmutable($dateInput)
                : new \DateTimeImmutable('today');

            $mode = (string) $request->request->get('mode', 'Cash');

            $result = $this->settlementService->settle($staff, $date, $mode);

            if (null === $result) {
                $this->addFlash('warning', 'No unpaid entries for this staff member.');

                return $this->redirect($selfUrl);
            }

            $this->addFlash('success', \sprintf(
                'Paid %s to %s — %d entries settled on voucher %s.',
                $this->numbers->inr($result['total']),
                $staff->getName(),
                $result['count'],
                $result['payment']->getNo(),
            ));

            return $this->redirect($backUrl);
        }

        $selectedId = $request->query->get('staff');
        $selected = null !== $selectedId ? $this->staffRepository->find((int) $selectedId) : null;

        return $this->render('admin/staff_settle.html.twig', [
            'page_title' => 'Settle Unpaid Wages',
            'outstanding' => $outstanding,
            'selected' => $selected,
            'today' => (new \DateTimeImmutable('today'))->format('Y-m-d'),
            'modes' => Payment::MODES,
            'next_payment_no' => $this->documentNumbers->peek(DocumentNumberService::PAYMENT),
            'back_url' => $backUrl,
            'action_url' => $selfUrl,
        ]);
    }

    /**
     * This list is read before a settlement run: whose work, over what dates, and
     * is it still unpaid. Those three lead, in that order.
     *
     * "Settled By" is filterable too, so the entries behind one payment voucher
     * can be pulled up after the fact.
     */
    public function configureFilters(Filters $filters): Filters
    {
        return $filters
            ->add(EntityFilter::new('staff', 'Staff Member'))
            ->add(DateTimeFilter::new('date', 'Date'))
            ->add(BooleanFilter::new('paid', 'Paid'))
            ->add(EntityFilter::new('plant', 'Plant'))
            ->add(TextFilter::new('workType', 'Work Type'))
            ->add(NumericFilter::new('amount', 'Amount'))
            ->add(NumericFilter::new('qty', 'Qty'))
            ->add(NumericFilter::new('rate', 'Rate'))
            ->add(EntityFilter::new('paymentVoucher', 'Settled By'));
    }

    /**
     * Who did the work and where on one row; what the work was worth on the
     * next. Rate and Amount are read as a pair against Qty, so all four share a
     * row on a desktop and fold into pairs before stacking.
     */
    public function configureFields(string $pageName): iterable
    {
        yield DateField::new('date', 'Date')->setColumns('col-12 col-md-4');

        yield AssociationField::new('staff', 'Staff Member')->setColumns('col-12 col-md-4');

        yield AssociationField::new('plant', 'Plant')->setColumns('col-12 col-md-4');

        yield TextField::new('workType', 'Work Type')
            ->setHelp('e.g. Stitching, Loading, Lamination')
            ->setColumns('col-12 col-md-6 col-xl-3');

        yield NumberField::new('qty', 'Qty')
            ->setNumDecimals(3)
            ->setFormTypeOption('attr', ['step' => '0.001'])
            ->setColumns('col-12 col-md-6 col-xl-3');

        yield MoneyField::new('rate', 'Rate')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2)
            ->setFormTypeOption('attr', ['step' => '0.01'])
            ->setHelp('Leave blank to use the staff member’s wage rate — or type one to override it')
            ->setColumns('col-12 col-md-6 col-xl-3');

        yield MoneyField::new('amount', 'Amount')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2)
            ->setFormTypeOption('disabled', true)
            ->setRequired(false)
            ->setColumns('col-12 col-md-6 col-xl-3');

        // Both are settlement outcomes rather than inputs, so they start a new
        // row; on the detail page they read side by side.
        yield BooleanField::new('paid', 'Paid')
            ->renderAsSwitch(false)
            ->setFormTypeOption('disabled', true)
            ->setHelp('Set by wage settlement')
            ->setColumns('col-12 col-md-4');

        yield AssociationField::new('paymentVoucher', 'Settled By')
            ->onlyOnDetail()
            ->setColumns('col-12 col-md-4');
    }

    /**
     * The rate autofill is attached to both form builders, so leaving Rate blank
     * fills it from the staff member's wage type on new entries and on edits.
     */
    public function createNewFormBuilder(EntityDto $entityDto, KeyValueStore $formOptions, AdminContext $context): FormBuilderInterface
    {
        return parent::createNewFormBuilder($entityDto, $formOptions, $context)
            ->addEventSubscriber($this->defaultRate);
    }

    public function createEditFormBuilder(EntityDto $entityDto, KeyValueStore $formOptions, AdminContext $context): FormBuilderInterface
    {
        return parent::createEditFormBuilder($entityDto, $formOptions, $context)
            ->addEventSubscriber($this->defaultRate);
    }

    /** Amount is always derived server-side; a posted value is never trusted. */
    public function persistEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if ($entityInstance instanceof StaffWork) {
            $entityInstance->recalculateAmount();
        }

        parent::persistEntity($em, $entityInstance);
    }

    public function updateEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if ($entityInstance instanceof StaffWork) {
            $entityInstance->recalculateAmount();
        }

        parent::updateEntity($em, $entityInstance);
    }
}
