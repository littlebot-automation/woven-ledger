<?php

declare(strict_types=1);

namespace App\Service;

use App\Controller\Admin\ItemCrudController;
use App\Controller\Admin\PartyCrudController;
use App\Controller\Admin\PaymentCrudController;
use App\Controller\Admin\PlantCrudController;
use App\Controller\Admin\PurchaseBillCrudController;
use App\Controller\Admin\ReceiptCrudController;
use App\Controller\Admin\SalesInvoiceCrudController;
use App\Controller\Admin\SettingsCrudController;
use App\Controller\Admin\StaffCrudController;
use App\Controller\Admin\StaffWorkCrudController;
use App\Entity\Item;
use App\Entity\Party;
use App\Entity\Payment;
use App\Entity\Plant;
use App\Entity\PurchaseBill;
use App\Entity\Receipt;
use App\Entity\SalesInvoice;
use App\Entity\Settings;
use App\Entity\Staff;
use App\Entity\StaffWork;

/**
 * Single source of truth for the sidebar — spec §2.
 *
 * Both the EasyAdmin menu and the standalone pages' own layout read this, so the
 * two shells cannot drift apart. Each CRUD entry carries its entity (for
 * EasyAdmin's MenuItem::linkToCrud) and its controller (so the standalone layout
 * can build a plain query-string admin URL without an AdminContext).
 */
class NavigationProvider
{
    /**
     * @return array<int, array{group: string, items: array<int, array<string, string>>}>
     */
    public function getGroups(): array
    {
        return [
            [
                'group' => 'Overview',
                'items' => [
                    ['id' => 'dashboard', 'label' => 'Dashboard', 'icon' => '📊', 'route' => 'admin'],
                ],
            ],
            [
                'group' => 'Masters',
                'items' => [
                    ['id' => 'parties', 'label' => 'Customers & Suppliers', 'icon' => '👥', 'entity' => Party::class, 'controller' => PartyCrudController::class],
                    ['id' => 'items', 'label' => 'Item & Inventory', 'icon' => '📦', 'entity' => Item::class, 'controller' => ItemCrudController::class],
                    ['id' => 'staff', 'label' => 'Staff Master', 'icon' => '🪪', 'entity' => Staff::class, 'controller' => StaffCrudController::class],
                ],
            ],
            [
                'group' => 'Transactions',
                'items' => [
                    ['id' => 'sales', 'label' => 'Sales Invoices', 'icon' => '🧾', 'entity' => SalesInvoice::class, 'controller' => SalesInvoiceCrudController::class],
                    ['id' => 'purchases', 'label' => 'Purchase Bills', 'icon' => '🛒', 'entity' => PurchaseBill::class, 'controller' => PurchaseBillCrudController::class],
                    ['id' => 'receipts', 'label' => 'Receipt Vouchers', 'icon' => '💵', 'entity' => Receipt::class, 'controller' => ReceiptCrudController::class],
                    ['id' => 'paymentsv', 'label' => 'Payment Vouchers', 'icon' => '💸', 'entity' => Payment::class, 'controller' => PaymentCrudController::class],
                    ['id' => 'staffwork', 'label' => 'Daily Staff Work', 'icon' => '👷', 'entity' => StaffWork::class, 'controller' => StaffWorkCrudController::class],
                ],
            ],
            [
                'group' => 'Insights',
                'items' => [
                    ['id' => 'ledger', 'label' => 'Party Ledger', 'icon' => '📒', 'route' => 'admin_ledger'],
                    ['id' => 'reports', 'label' => 'Business Reports', 'icon' => '📈', 'route' => 'admin_reports'],
                ],
            ],
            [
                'group' => 'System',
                'items' => [
                    ['id' => 'plants', 'label' => 'Plants', 'icon' => '🏭', 'entity' => Plant::class, 'controller' => PlantCrudController::class],
                    ['id' => 'settings', 'label' => 'Business Settings', 'icon' => '⚙️', 'entity' => Settings::class, 'controller' => SettingsCrudController::class],
                    ['id' => 'backup', 'label' => 'Backup & Restore', 'icon' => '💾', 'route' => 'admin_backup'],
                ],
            ],
        ];
    }

    /** Page title and subtitle, spec §2. */
    public function getPageMeta(string $id): array
    {
        return [
            'dashboard' => ['Dashboard', 'Overview of your business today'],
            'parties' => ['Customers & Suppliers', 'Manage your party master records'],
            'items' => ['Item & Inventory', 'Fabric rolls, finished bags and plant-wise stock'],
            'staff' => ['Staff Master', 'Workers across your plants'],
            'sales' => ['Sales Invoices', 'Bill your buyers and auto-adjust stock'],
            'purchases' => ['Purchase Bills', 'Record fabric roll purchases from suppliers'],
            'receipts' => ['Receipt Vouchers', 'Money received from parties'],
            'paymentsv' => ['Payment Vouchers', 'Money paid to parties or staff'],
            'staffwork' => ['Daily Staff Work', 'Log work and settle staff wages'],
            'ledger' => ['Party Ledger', 'Running account for any party'],
            'reports' => ['Business Reports', 'Sales, purchase, stock and outstanding summaries'],
            'plants' => ['Plants', 'Manufacturing units you operate'],
            'settings' => ['Business Settings', 'Company details, plants, numbering & data'],
            'backup' => ['Backup & Restore', 'Export or restore your whole database'],
        ][$id] ?? ['Woven Ledger', ''];
    }
}
