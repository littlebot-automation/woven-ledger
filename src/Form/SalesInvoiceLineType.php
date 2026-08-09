<?php

declare(strict_types=1);

namespace App\Form;

use App\Entity\Item;
use App\Entity\SalesInvoiceLine;
use App\Form\EventListener\DefaultRateFromItemListener;
use Symfony\Bridge\Doctrine\Form\Type\EntityType;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\NumberType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;

class SalesInvoiceLineType extends AbstractType
{
    public function __construct(
        private readonly DefaultRateFromItemListener $defaultRate,
    ) {
    }

    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder->addEventSubscriber($this->defaultRate);

        $builder
            ->add('item', EntityType::class, [
                'class' => Item::class,
                'choice_label' => 'name',
                'placeholder' => 'Select item',
                'required' => false,
                'label' => 'Item',
            ])
            ->add('qty', NumberType::class, [
                'scale' => 3,
                'required' => false,
                'label' => 'Qty',
                'attr' => ['step' => '0.001'],
            ])
            ->add('rate', NumberType::class, [
                'scale' => 2,
                'required' => false,
                'label' => 'Rate',
                'attr' => ['step' => '0.01'],
            ]);
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => SalesInvoiceLine::class,
        ]);
    }
}
