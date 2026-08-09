<?php

declare(strict_types=1);

namespace App\Form\EventListener;

use App\Entity\Staff;
use App\Repository\StaffRepository;
use Symfony\Component\EventDispatcher\EventSubscriberInterface;
use Symfony\Component\Form\Event\PreSubmitEvent;
use Symfony\Component\Form\FormEvents;

/**
 * Fills a work entry's rate from the chosen staff member's wage type.
 *
 * PRE_SUBMIT because the decision is made from what was actually posted: the
 * staff member is still a raw id and the rate a raw string at this point, which
 * is exactly what we need to tell "left blank" apart from "typed a number".
 * A rate the user entered is never overwritten.
 *
 * @see https://symfony.com/doc/current/form/dynamic_form_modification.html
 */
class DefaultRateFromStaffListener implements EventSubscriberInterface
{
    public function __construct(
        private readonly StaffRepository $staff,
    ) {
    }

    public static function getSubscribedEvents(): array
    {
        return [FormEvents::PRE_SUBMIT => 'fillRate'];
    }

    public function fillRate(PreSubmitEvent $event): void
    {
        $data = $event->getData();

        if (!\is_array($data)) {
            return;
        }

        if (0.0 !== (float) ($data['rate'] ?? 0)) {
            return;
        }

        $staffId = (int) ($data['staff'] ?? 0);

        if (0 === $staffId) {
            return;
        }

        $staff = $this->staff->find($staffId);

        if (!$staff instanceof Staff) {
            return;
        }

        $data['rate'] = number_format($staff->getEffectiveRate(), 2, '.', '');
        $event->setData($data);
    }
}
