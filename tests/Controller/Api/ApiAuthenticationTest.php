<?php

declare(strict_types=1);

namespace App\Tests\Controller\Api;

use App\Tests\ApiTestCase;

/**
 * The gate itself, rather than anything behind it.
 *
 * Every other API test arrives already logged in, courtesy of the base class, so
 * this is the only place that proves the door is actually shut — that /api is
 * unreachable without a token, that a wrong password buys nothing, and that the
 * token the login hands out is one the API accepts.
 */
class ApiAuthenticationTest extends ApiTestCase
{
    public function testTheApiRejectsACallerWithoutAToken(): void
    {
        $this->forgetToken();

        $body = $this->send('GET', '/api/plants');

        $this->assertSame(401, $this->statusCode());
        $this->assertSame(401, $body['code']);
    }

    public function testLoggingInWithTheWrongPasswordIsRefused(): void
    {
        $body = $this->requestToken(self::API_USERNAME, 'not-the-password');

        $this->assertSame(401, $this->statusCode());
        $this->assertArrayNotHasKey('token', $body);
    }

    public function testLoggingInWithTheRightPasswordReturnsAToken(): void
    {
        $body = $this->requestToken(self::API_USERNAME, self::API_PASSWORD);

        $this->assertSame(200, $this->statusCode());
        $this->assertArrayHasKey('token', $body);
        $this->assertNotSame('', $body['token']);
    }

    public function testThatTokenOpensARealEndpoint(): void
    {
        // A fresh login rather than the one setUp() did, so the assertion covers
        // the whole round trip: credentials in, token out, token accepted.
        $this->forgetToken();
        $token = $this->requestToken(self::API_USERNAME, self::API_PASSWORD)['token'];

        $this->client->setServerParameter('HTTP_AUTHORIZATION', 'Bearer '.$token);
        $plants = $this->send('GET', '/api/plants');

        $this->assertSame(200, $this->statusCode());
        $this->assertSame('Plant A', $plants[0]['name']);
    }
}
