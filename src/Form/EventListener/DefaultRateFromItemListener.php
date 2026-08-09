<?php

declare(strict_types=1);

namespace App\Form\EventListener;

use App\Entity\Item;
use App\Repository\ItemRepository;
use Symfony\Component\EventDispatcher\EventSubscriberInterface;
use Symfony\Component\Form\Event\PreSubmitEvent;
use Symfony\Component\Form\FormEvents;

/**
 * Fills a document line's rate from the chosen item's default rate.
 *
 * PRE_SUBMIT because the decision is made from what was actually posted: the
 * item is still a raw id and the rate a raw string at this point, which is
 * exactly what we need to tell "left blank" apart from "typed a number".
 * A rate the user entered is never overwritten.
 *
 * @see https://symfony.com/doc/current/form/dynamic_form_modification.html
 */
class DefaultRateFromItemListener implements EventSubscriberInterface
{
    public function __construct(
        private readonly ItemRepository $items,
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

        // A rate the user typed wins; 0 and blank both count as "not given",
        // matching how an untouched line renders.
        if (0.0 !== (float) ($data['rate'] ?? 0)) {
            return;
        }

        $itemId = (int) ($data['item'] ?? 0);

        if (0 === $itemId) {
            return;
        }

        $item = $this->items->find($itemId);

        if (!$item instanceof Item) {
            return;
        }

        $data['rate'] = number_format($item->getDefaultRate(), 2, '.', '');
        $event->setData($data);
    }
}
