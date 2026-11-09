import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import CodeBlock from '@theme/CodeBlock';
import Heading from '@theme/Heading';
import HomepageFeatures from '@site/src/components/HomepageFeatures';

import styles from './index.module.css';

const EXAMPLE = `public class HelloFunction {

    @Function(name = "hello")
    @HttpTrigger(method = "GET", path = "/hello/{name}")
    public String handleRequest(@PathParam String name) {
        return "hello " + name;
    }
}`;

const BUILD = `# Same code, different provider:
mvn clean install -Paws-terraform
mvn clean install -Pazure-terraform
mvn clean install -Pgcp-terraform
mvn clean install -Pspringboot-http   # on-premise`;

function HomepageHeader() {
  return (
    <header className={clsx('hero hero--primary', styles.heroBanner)}>
      <div className={clsx('container', styles.heroGrid)}>
        <div className={styles.heroText}>
          <p className={styles.eyebrow}>Dukehoff Cloudhopper</p>
          <Heading as="h1" className="hero__title">
            Write once, run anywhere. Revisited.
          </Heading>
          <p className="hero__subtitle">
            Your functions don't know which cloud they run on. One provider got
            too expensive? Data has to stay in the EU? The customer wants it
            on-premise? Change the target in your build, not your code.
          </p>
          <p className={styles.heroTargets}>
            Targets: AWS Lambda, Azure Functions, Google Cloud Functions, and a
            Spring Boot service that runs on any server.
          </p>
          <div className={styles.buttons}>
            <Link
              className="button button--secondary button--lg"
              to="/docs/guides/quickstart">
              Quickstart
            </Link>
            <Link
              className={clsx('button button--outline button--lg', styles.buttonLight)}
              to="/docs/intro">
              How it works
            </Link>
          </div>
        </div>
        <div className={styles.heroCode}>
          <CodeBlock language="java">{EXAMPLE}</CodeBlock>
          <CodeBlock language="bash">{BUILD}</CodeBlock>
        </div>
      </div>
    </header>
  );
}

function StatusNote() {
  return (
    <section className={styles.section}>
      <div className="container">
        <div className="alert alert--info" role="note">
          <strong>Pre-release.</strong> Version 1.0 is not on Maven Central yet.
          To try it today, build it from source; the{' '}
          <Link to="/docs/guides/quickstart">Quickstart</Link> shows how.
        </div>
      </div>
    </section>
  );
}

function LicenseAndMaker() {
  return (
    <section className={clsx(styles.section, styles.sectionAlt)}>
      <div className="container">
        <div className="row">
          <div className="col col--6">
            <Heading as="h2">Open source, and the output is yours</Heading>
            <p>
              Dukehoff Cloudhopper is licensed under the GNU General Public License,
              version 3, with the Classpath Exception and a Generator Output
              Exception. Everything it generates for your project, such as
              handler classes, Terraform and API descriptions, you may use,
              modify and distribute under terms of your choice.
            </p>
            <p>
              <Link href="https://github.com/dukehoff-gmbh/dukehoff-cloudhopper/blob/main/LICENSE-EXCEPTIONS">
                Read the exceptions
              </Link>
            </p>
          </div>
          <div className="col col--6">
            <Heading as="h2">Built by Dukehoff GmbH</Heading>
            <p>
              Dukehoff Cloudhopper is developed by Dukehoff GmbH near Munich. If you
              want help moving Java services to serverless, between cloud
              providers or onto your own servers, talk to us.
            </p>
            <p>
              <Link href="https://dukehoff.com/en/contact/">Contact Dukehoff</Link>
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}

export default function Home() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title="Write once, run anywhere. Revisited."
      description={siteConfig.customFields.description}>
      <HomepageHeader />
      <main>
        <StatusNote />
        <HomepageFeatures />
        <LicenseAndMaker />
      </main>
    </Layout>
  );
}
