/*
 * Copyright (C) 2009-2015 Typesafe Inc. <http://www.typesafe.com>
 */
package play.mvc;

import play.inject.Injector;
import play.libs.F;
import play.mvc.Http.*;

import java.lang.annotation.*;
import javax.inject.Inject;

/**
 * Defines several security helpers.
 */
public class Security {
    
    /**
     * Wraps the annotated action in an <code>AuthenticatedAction</code>.
     */
    @With(AuthenticatedAction.class)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Authenticated {
        Class<? extends Authenticator> value() default Authenticator.class;
    }
    
    /**
     * Wraps another action, allowing only authenticated HTTP requests.
     * <p>
     * The user name is retrieved from the session cookie, and added to the HTTP request's
     * <code>username</code> attribute.
     */
    public static class AuthenticatedAction extends Action<Authenticated> {

        private final Injector injector;

        @Inject
        public AuthenticatedAction(Injector injector) {
            this.injector = injector;
        }

        public F.Promise<Result> call(final Context ctx) {
            try {
                Authenticator authenticator = injector.instanceOf(configuration.value());
                String username = authenticator.getUsername(ctx);
                if(username == null) {
                    Result unauthorized = authenticator.onUnauthorized(ctx);
                    return F.Promise.pure(unauthorized);
                } else {
                    try {
                        ctx.request().setUsername(username);
                        return delegate.call(ctx).transform(
                            result -> {
                                ctx.request().setUsername(null);
                                return result;
                            },
                            throwable -> {
                                ctx.request().setUsername(null);
                                return throwable;
                            }
                        );
                    } catch(Exception e) {
                        ctx.request().setUsername(null);
                        throw e;
                    }
                }
            } catch(RuntimeException e) {
                throw e;
            } catch(Throwable t) {
                throw new RuntimeException(t);
            }
        }

    }
    
    /**
     * Handles authentication.
     */
    public static class Authenticator extends Results {
        
        /**
         * Retrieves the username from the HTTP context; the default is to read from the session cookie.
         *
         * @return null if the user is not authenticated.
         */
        public String getUsername(Context ctx) {
            return ctx.session().get("username");
        }
        
        /**
         * Generates an alternative result if the user is not authenticated; the default a simple '401 Not Authorized' page.
         */
        public Result onUnauthorized(Context ctx) {
            return unauthorized(views.html.defaultpages.unauthorized.render());
        }
        
    }
    
    
}
